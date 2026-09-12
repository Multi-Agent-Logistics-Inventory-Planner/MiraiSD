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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory/log.md, review-driven fix: T-6c-6 P1 (destination-row locking gap).
 * Before this fix, {@code StockMovementService.resolveTransferLockIds} only planned a lock for a
 * transfer's destination when a {@code location_inventory} row already existed for that
 * {@code (location, product)} pair at planning time -- a brand-new destination had nothing to
 * lock, and {@code executeTransfer}'s own later, separate {@code
 * findByLocation_IdAndProduct_Id(...).orElseGet(create)} fallback query could find (or race to
 * create) that row with no lock ever held on it. Two concurrent transfers landing on the same
 * never-before-used destination could each read-modify-write its quantity unlocked, losing an
 * update exactly as {@code batchAdjustInventory} could before T-6c-6's original fix.
 * <p>
 * This test proves the fix closes that specific gap: two different source rows, each with ample
 * stock, transferred concurrently to the SAME destination {@code (location, product)} pair that
 * does not exist anywhere in the database when the race begins. Uses the same forced-overlap
 * technique as {@link StockMovementServiceConcurrentAdjustIT} and {@code SiteProductConcurrencyIT}
 * -- a third, independently managed transaction inserts (but never commits) a placeholder
 * {@code location_inventory} row at the destination key and holds the transaction open, so both
 * racing {@link StockMovementService#transferInventory} calls' {@code INSERT ... ON CONFLICT}
 * destination-creation step is forced to actually block on it (confirmed via
 * {@code pg_stat_activity} polling for 2 backends in {@code wait_event_type = 'Lock'}) before the
 * holder rolls back -- proving genuinely overlapping, concurrent transactions rather than two
 * sequential calls that happen to look concurrent because of JVM thread scheduling. The holder
 * rolls back (never commits) so that, from the database's perspective, no row ever existed at this
 * key before the two real transfers raced to create/use it themselves.
 */
class StockMovementServiceConcurrentTransferNewDestinationIT extends BaseKafkaIntegrationTest {

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
                .name("Concurrent Transfer IT Category " + suffix)
                .slug("concurrent-transfer-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("CONCURRENT-XFER-IT-" + suffix)
                .name("Concurrent Transfer IT Product " + suffix)
                .category(category)
                .isActive(true)
                .quantity(0)
                .build());
    }

    private Location newLocation(String label) {
        String suffix = label + "-" + System.nanoTime();
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
        return locationRepository.save(Location.builder()
                .storageLocation(storage)
                .locationCode("CXFER-" + suffix)
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
    void concurrentTransfersToSameNotYetExistingDestinationSerializeWithoutLostUpdate() throws Exception {
        Product product = newProduct("Race");

        Location sourceLocationA = newLocation("SourceA");
        Location sourceLocationB = newLocation("SourceB");
        Location destinationLocation = newLocation("Dest");

        LocationInventory sourceA = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceLocationA)
                .site(sourceLocationA.getStorageLocation().getSite())
                .product(product)
                .quantity(100)
                .build());
        LocationInventory sourceB = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceLocationB)
                .site(sourceLocationB.getStorageLocation().getSite())
                .product(product)
                .quantity(100)
                .build());

        // Confirm the destination genuinely does not exist yet, anywhere, before the race starts.
        assertThat(locationInventoryRepository.findByLocation_IdAndProduct_Id(
                destinationLocation.getId(), product.getId())).isEmpty();

        int deltaA = 30;
        int deltaB = 20;
        int expectedFinalQuantity = deltaA + deltaB;

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate holderTemplate = new TransactionTemplate(transactionManager);

        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = lockExecutor.submit(() -> holderTemplate.executeWithoutResult(status -> {
            // Same statement shape as the production insertLocationInventoryIfAbsent path, minus
            // ON CONFLICT (nothing to conflict with yet -- this IS the first attempt at this key).
            // Held open, uncommitted, so both racing transfers' own INSERT ... ON CONFLICT DO
            // NOTHING calls are forced to block waiting for this transaction's outcome.
            jdbcTemplate.update(
                    "INSERT INTO location_inventory (id, location_id, site_id, product_id, quantity, "
                            + "created_at, updated_at) VALUES (?, ?, ?, ?, 0, now(), now())",
                    UUID.randomUUID(), destinationLocation.getId(),
                    destinationLocation.getStorageLocation().getSite().getId(), product.getId());
            lockAcquired.countDown();
            try {
                if (!releaseLock.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release signal never arrived");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            // Roll back: from the database's perspective, no row ever really existed at this key
            // before the two real transfers below raced to create/use it themselves.
            status.setRollbackOnly();
        }));

        assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                .as("the placeholder insert must be taken before the racing transfers start")
                .isTrue();

        ExecutorService raceExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = raceExecutor.submit(() -> stockMovementService.transferInventory(
                    transferTo(sourceA.getId(), destinationLocation.getId(), deltaA)));
            Future<?> futureB = raceExecutor.submit(() -> stockMovementService.transferInventory(
                    transferTo(sourceB.getId(), destinationLocation.getId(), deltaB)));

            // Both transfers' destination-creation INSERT ... ON CONFLICT DO NOTHING blocks on the
            // externally held, uncommitted placeholder row -- proof both are genuinely concurrent,
            // overlapping transactions, not two sequential calls that happen to look concurrent.
            awaitBackendsWaitingOnALock(2, 10_000);
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            List<LocationInventory> destinationRows = locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                    destinationLocation.getId(), List.of(product.getId()));
            assertThat(destinationRows)
                    .as("exactly one location_inventory row must exist at the destination key, "
                            + "never two rows from a lost creation race")
                    .hasSize(1);

            LocationInventory destinationInventory = destinationRows.get(0);
            assertThat(destinationInventory.getQuantity())
                    .as("the destination's final quantity must equal the combined effect of both "
                            + "transfers, not a lost update from an unlocked read-modify-write")
                    .isEqualTo(expectedFinalQuantity);

            LocationInventory finalSourceA = locationInventoryRepository.findById(sourceA.getId()).orElseThrow();
            LocationInventory finalSourceB = locationInventoryRepository.findById(sourceB.getId()).orElseThrow();
            assertThat(finalSourceA.getQuantity()).isEqualTo(100 - deltaA);
            assertThat(finalSourceB.getQuantity()).isEqualTo(100 - deltaB);

            List<StockMovement> movements = stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId());
            assertThat(movements)
                    .as("exactly one withdrawal + one deposit movement per transfer call, "
                            + "no duplicates and no lost writes")
                    .hasSize(4);

            List<StockMovement> deposits = movements.stream()
                    .filter(m -> destinationLocation.getId().equals(m.getToLocationId()) && m.getQuantityChange() > 0)
                    .sorted(Comparator.comparing(StockMovement::getAt))
                    .toList();
            assertThat(deposits)
                    .as("exactly one deposit movement per transfer landed on the destination")
                    .hasSize(2);

            StockMovement firstDeposit = deposits.get(0);
            StockMovement secondDeposit = deposits.get(1);
            assertThat(firstDeposit.getPreviousQuantity())
                    .as("the destination started at zero -- it did not exist before this race")
                    .isEqualTo(0);
            assertThat(secondDeposit.getPreviousQuantity())
                    .as("the second deposit must see the first deposit's committed result, "
                            + "not a stale pre-lock value of zero")
                    .isEqualTo(firstDeposit.getCurrentQuantity());
            assertThat(secondDeposit.getCurrentQuantity()).isEqualTo(expectedFinalQuantity);
        } finally {
            raceExecutor.shutdownNow();
            lockExecutor.shutdownNow();
        }
    }
}
