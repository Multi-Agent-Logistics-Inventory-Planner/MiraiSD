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
 * .specs/phase-6-inventory/log.md, review-driven fix round 2: T-6c-6 P1 findings, bug 1 ("existing
 * destination can disappear before locking"). Before this fix, an existing destination's id was
 * resolved for locking via a scalar {@code findIdByLocation_IdAndProduct_Id} read that was NOT
 * itself a lock -- {@code INSERT ... ON CONFLICT DO NOTHING} is a true no-op against an existing
 * row, so there was a real, unguarded window between "found this id" and "locked this id" in which
 * another transaction could delete the row out from under the read. After the fix, {@code
 * StockMovementService.ensureAndLockInventoryRow} finds a row's id via {@code
 * findIdByLocation_IdAndProduct_IdForUpdate} -- a scalar, {@code PESSIMISTIC_WRITE} query -- so
 * finding IS locking; there is no separate read-then-lock step for a concurrent delete to race
 * through.
 * <p>
 * This test forces exactly that danger window deterministically: an existing destination row is
 * held under an externally managed, uncommitted {@code SELECT ... FOR UPDATE} while two concurrent
 * {@link StockMovementService#transferInventory} calls target it (by location, the implicit-
 * destination path, so they re-derive the row's current id rather than pinning a specific id up
 * front). Once both racing transfers are confirmed genuinely blocked on that external lock (via
 * {@code pg_stat_activity} polling, not timing), the external holder deletes the row and inserts a
 * brand-new row at the exact same {@code (location, product)} key -- a real delete-and-recreate,
 * not a placeholder that gets rolled back -- then commits. The two blocked transfers must then
 * correctly resolve, lock, and mutate whichever row actually exists at that key once they unblock:
 * neither may report a false "not found," neither may silently write against a since-vanished row,
 * and their combined effect on the recreated row's quantity must be fully serialized (no lost
 * update).
 */
class StockMovementServiceConcurrentTransferExistingDestinationRaceIT extends BaseKafkaIntegrationTest {

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
                .name("Concurrent Destination Race IT Category " + suffix)
                .slug("concurrent-destination-race-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("CONCURRENT-DEST-RACE-IT-" + suffix)
                .name("Concurrent Destination Race IT Product " + suffix)
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
                .locationCode("CXDESTRACE-" + suffix)
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
    void concurrentTransfersSurviveDestinationDeleteAndRecreateWithoutLostUpdateOrFalseNotFound() throws Exception {
        Product product = newProduct("Race");

        Location sourceLocationA = newLocation("SourceA");
        Location sourceLocationB = newLocation("SourceB");
        Location destinationLocation = newLocation("Dest");
        UUID destinationSiteId = destinationLocation.getStorageLocation().getSite().getId();

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

        int baselineQuantity = 5;
        LocationInventory originalDestination = locationInventoryRepository.save(LocationInventory.builder()
                .location(destinationLocation)
                .site(destinationLocation.getStorageLocation().getSite())
                .product(product)
                .quantity(baselineQuantity)
                .build());

        int deltaA = 30;
        int deltaB = 20;
        int expectedFinalQuantity = baselineQuantity + deltaA + deltaB;

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate holderTemplate = new TransactionTemplate(transactionManager);

        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = lockExecutor.submit(() -> holderTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList(
                    "SELECT id FROM location_inventory WHERE id = ? FOR UPDATE", originalDestination.getId());
            lockAcquired.countDown();
            try {
                if (!releaseLock.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release signal never arrived");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            // While the two racing transfers are confirmed blocked waiting on the row we're
            // holding, delete it and insert a brand-new row at the exact same (location, product)
            // key with a different id -- a genuine delete-and-recreate, committed here (not rolled
            // back), so the danger window this fix closes is actually exercised: the two blocked
            // transfers must resolve to whichever row really exists once they unblock, not a stale
            // reference to the row that is about to be gone.
            jdbcTemplate.update("DELETE FROM location_inventory WHERE id = ?", originalDestination.getId());
            jdbcTemplate.update(
                    "INSERT INTO location_inventory (id, location_id, site_id, product_id, quantity, "
                            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
                    UUID.randomUUID(), destinationLocation.getId(), destinationSiteId, product.getId(),
                    baselineQuantity);
        }));

        assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                .as("the original destination row's lock must be acquired before the racing transfers start")
                .isTrue();

        ExecutorService raceExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = raceExecutor.submit(() -> stockMovementService.transferInventory(
                    transferTo(sourceA.getId(), destinationLocation.getId(), deltaA)));
            Future<?> futureB = raceExecutor.submit(() -> stockMovementService.transferInventory(
                    transferTo(sourceB.getId(), destinationLocation.getId(), deltaB)));

            // Both transfers' ensureAndLockInventoryRow lookup blocks on the externally held row --
            // proof both are genuinely concurrent, overlapping transactions, not two sequential
            // calls that happen to look concurrent because of JVM thread scheduling.
            awaitBackendsWaitingOnALock(2, 10_000);
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            List<LocationInventory> destinationRows = locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                    destinationLocation.getId(), List.of(product.getId()));
            assertThat(destinationRows)
                    .as("exactly one row must exist at the destination key after the race -- the "
                            + "recreated row, correctly found and locked, never a resurrected or "
                            + "duplicate row")
                    .hasSize(1);

            LocationInventory finalDestination = destinationRows.get(0);
            assertThat(finalDestination.getId())
                    .as("the surviving row must be the recreated one, not the original id that was "
                            + "deleted mid-race -- proving neither transfer silently wrote through a "
                            + "stale reference to the deleted row")
                    .isNotEqualTo(originalDestination.getId());
            assertThat(finalDestination.getQuantity())
                    .as("both transfers' quantities must be fully applied to the recreated row, with "
                            + "no lost update from the delete-and-recreate race")
                    .isEqualTo(expectedFinalQuantity);

            LocationInventory finalSourceA = locationInventoryRepository.findById(sourceA.getId()).orElseThrow();
            LocationInventory finalSourceB = locationInventoryRepository.findById(sourceB.getId()).orElseThrow();
            assertThat(finalSourceA.getQuantity()).isEqualTo(100 - deltaA);
            assertThat(finalSourceB.getQuantity()).isEqualTo(100 - deltaB);

            List<StockMovement> movements = stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId());
            assertThat(movements)
                    .as("exactly one withdrawal + one deposit movement per transfer call, no "
                            + "duplicates and no lost writes despite the mid-race delete/recreate")
                    .hasSize(4);
        } finally {
            raceExecutor.shutdownNow();
            lockExecutor.shutdownNow();
        }
    }
}
