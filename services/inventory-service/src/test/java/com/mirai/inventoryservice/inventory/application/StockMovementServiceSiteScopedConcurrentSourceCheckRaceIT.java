package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory/log.md, review-driven fix: 6d P1 finding (external review) -- "concurrent
 * batch transfers can lose source debits." Before this fix, {@code
 * StockMovementService#requireInventoryBelongsToSite} (called from both the site-scoped {@code
 * transferInventory(UUID siteId, ...)} and {@code batchTransferInventory(UUID siteId, ...)}
 * overloads) confirmed site membership via {@link LocationInventoryRepository#findByIdAndSite_Id},
 * which returns a full, JOIN-FETCH'd {@code LocationInventory} ENTITY -- not a scalar projection.
 * This ran before {@code planTransfers}/{@code lockPlannedRows} ever acquired a lock, so it
 * populated the transaction's Hibernate persistence context with an unlocked snapshot of the row's
 * quantity. The later, "locked" read inside {@code transferInventory}/{@code
 * batchTransferInventory} ({@code findById}/{@code findAllByIdWithGraph}) does not re-query scalar
 * state for an already-managed entity -- Hibernate silently returns the same stale Java object --
 * so the transfer computes its debit from the pre-lock quantity even though a real Postgres row lock
 * was, by then, correctly held.
 * <p>
 * This test proves the resulting lost update using two REAL, concurrent, site-scoped {@link
 * StockMovementService#transferInventory(UUID, UUID, TransferInventoryRequestDTO)} calls that share
 * one source inventory row, forced to interleave around the site-check/lock boundary: an externally
 * held {@code SELECT ... FOR UPDATE} on the shared source row is taken first, so both racing
 * transfers' unlocked {@code requireInventoryBelongsToSite} reads complete (and cache the row's
 * original quantity) before either can acquire {@code ensureAndLockInventoryRow}'s real lock. Once
 * both are confirmed genuinely blocked on that lock (via {@code pg_stat_activity} polling, not
 * timing), the external holder releases without mutating the row -- exactly reproducing two
 * overlapping transactions that both saw the same unlocked pre-lock quantity. After the fix, each
 * transaction's first entity-level touch of the source row happens only after it holds the row's
 * real lock, so each debit is applied against the true, serialized quantity and both debits survive.
 */
class StockMovementServiceSiteScopedConcurrentSourceCheckRaceIT extends BaseKafkaIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Product newProduct(String label) {
        String suffix = label + "-" + System.nanoTime();
        Category category = categoryRepository.save(Category.builder()
                .name("Site Scoped Source Race IT Category " + suffix)
                .slug("site-scoped-source-race-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("SITE-SCOPED-SRC-RACE-IT-" + suffix)
                .name("Site Scoped Source Race IT Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    private Location newLocation(String label, Site site, StorageLocation storage) {
        String suffix = label + "-" + System.nanoTime();
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("SSSRC-" + suffix)
                .build());
    }

    /** Polls until at least {@code n} backends are actively blocked on a lock, or times out. */
    private void awaitBackendsWaitingOnALock(int n, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND wait_event_type = 'Lock'",
                    Integer.class);
            if (waiting != null && waiting >= n) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("timed out waiting for " + n + " backends to block on a lock");
    }

    private TransferInventoryRequestDTO transferTo(UUID sourceInventoryId, UUID destLocationId, int quantity) {
        return TransferInventoryRequestDTO.builder()
                .sourceLocationType(LocationType.BOX_BIN)
                .sourceInventoryId(sourceInventoryId)
                .destinationLocationType(LocationType.BOX_BIN)
                .destinationLocationId(destLocationId)
                .quantity(quantity)
                .build();
    }

    @Test
    void concurrentSiteScopedTransfersSharingASourceRowApplyBothDebitsWithNoLostUpdate() throws Exception {
        Site site = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder().code("MAIN").name("MAIN").build()));
        StorageLocation storage = storageLocationRepository
                .findByCodeAndSite_Code("BOX_BINS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(site)
                        .code("BOX_BINS")
                        .name("Box Bins")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(1)
                        .build()));

        Product product = newProduct("SrcRace");
        Location sourceLocation = newLocation("Source", site, storage);
        Location destinationLocationA = newLocation("DestA", site, storage);
        Location destinationLocationB = newLocation("DestB", site, storage);

        int baselineQuantity = 100;
        LocationInventory source = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceLocation)
                .site(site)
                .product(product)
                .quantity(baselineQuantity)
                .build());

        int deltaA = 30;
        int deltaB = 47;
        int expectedFinalQuantity = baselineQuantity - deltaA - deltaB;
        UUID actorId = UUID.randomUUID();

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate holderTemplate = new TransactionTemplate(transactionManager);

        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = lockExecutor.submit(() -> holderTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList(
                    "SELECT id FROM location_inventory WHERE id = ? FOR UPDATE", source.getId());
            lockAcquired.countDown();
            try {
                if (!releaseLock.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release signal never arrived");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            // Released without mutating the row -- the danger this test reproduces comes purely
            // from both racers' unlocked site-check reads happening before either's real lock
            // acquisition, not from any externally injected data change.
        }));

        assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                .as("the shared source row's external lock must be acquired before the racing transfers start")
                .isTrue();

        ExecutorService raceExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = raceExecutor.submit(() -> stockMovementService.transferInventory(
                    site.getId(), actorId, transferTo(source.getId(), destinationLocationA.getId(), deltaA)));
            Future<?> futureB = raceExecutor.submit(() -> stockMovementService.transferInventory(
                    site.getId(), actorId, transferTo(source.getId(), destinationLocationB.getId(), deltaB)));

            // Both transfers' requireInventoryBelongsToSite reads are plain (non-locking) SELECTs,
            // so they complete immediately even while the external holder has the row FOR UPDATE.
            // Both then block for real inside ensureAndLockInventoryRow's locking find -- proof both
            // transactions already cached the row's pre-lock quantity before either could lock it.
            awaitBackendsWaitingOnALock(2, 10_000);
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            LocationInventory finalSource = locationInventoryRepository.findById(source.getId()).orElseThrow();
            assertThat(finalSource.getQuantity())
                    .as("both concurrent transfers' debits must be applied to the shared source row "
                            + "with no lost update, even though both read the row's site membership "
                            + "before either acquired the row's real lock")
                    .isEqualTo(expectedFinalQuantity);

            List<StockMovement> movements = stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId());
            assertThat(movements)
                    .as("exactly one withdrawal + one deposit movement per transfer call")
                    .hasSize(4);
        } finally {
            raceExecutor.shutdownNow();
            lockExecutor.shutdownNow();
        }
    }
}
