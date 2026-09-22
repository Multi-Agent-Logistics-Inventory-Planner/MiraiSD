package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.BatchAdjustLineDTO;
import com.mirai.inventoryservice.inventory.api.BatchAdjustStockRequestDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory/log.md, T-6c-6 (F-6c-5): the concurrency proof 6b explicitly deferred
 * to 6c. Before this task, {@link StockMovementService#batchAdjustInventory} preloaded
 * {@link LocationInventory} rows with a plain, unlocked read and later wrote the mutated quantity
 * back with no lock of any kind, so two concurrent adjustments against the same
 * {@code (location, product)} pair could both read the same starting quantity and one write would
 * silently clobber the other -- a lost update -- with only the DB's
 * {@code CHECK (quantity >= 0)} as an accidental backstop.
 * <p>
 * Same technique as {@link com.mirai.inventoryservice.catalog.application.SiteProductConcurrencyIT}:
 * a third, independently managed transaction takes a real {@code SELECT ... FOR UPDATE} on the
 * target row and holds it open, so both racing {@code batchAdjustInventory} calls are forced to
 * actually block at the same instant -- proving they are genuine, overlapping concurrent
 * transactions, not two sequential calls that happen to look concurrent because of JVM thread
 * scheduling. Releasing the lock lets real PostgreSQL row-level locking decide serialization
 * order; either order is an acceptable, correct outcome, which is exactly why the assertions below
 * check the *combined* effect (final quantity, movement count, no negative quantity) rather than
 * which specific thread went first.
 * <p>
 * Uses real Postgres (Testcontainers, via {@link BaseKafkaIntegrationTest}), not H2: this proof is
 * about actual multi-transaction row-locking semantics, which H2's test profile does not exercise
 * with the same fidelity as real PostgreSQL (F-6c-5, T-6c-6's task text).
 */
class StockMovementServiceConcurrentAdjustIT extends BaseKafkaIntegrationTest {

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
                .name("Concurrent Adjust IT Category " + suffix)
                .slug("concurrent-adjust-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("CONCURRENT-ADJUST-IT-" + suffix)
                .name("Concurrent Adjust IT Product " + suffix)
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
                .locationCode("CXADJ-" + suffix)
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

    private BatchAdjustStockRequestDTO singleLineAdjust(UUID locationId, UUID inventoryId, int quantityChange) {
        return BatchAdjustStockRequestDTO.builder()
                .locationType(LocationType.BOX_BIN)
                .locationId(locationId)
                .adjustments(List.of(BatchAdjustLineDTO.builder()
                        .inventoryId(inventoryId)
                        .quantityChange(quantityChange)
                        .build()))
                .reason(StockMovementReason.ADJUSTMENT)
                .build();
    }

    @Test
    void concurrentBatchAdjustOnSameInventoryRowSerializesWithoutLostUpdate() throws Exception {
        Product product = newProduct("Race");
        Location location = newLocation("Race");
        LocationInventory inventory = locationInventoryRepository.save(LocationInventory.builder()
                .location(location)
                .site(location.getStorageLocation().getSite())
                .product(product)
                .quantity(100)
                .build());

        int deltaA = -30;
        int deltaB = -20;
        int expectedFinalQuantity = 100 + deltaA + deltaB;

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate lockTemplate = new TransactionTemplate(transactionManager);

        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = lockExecutor.submit(() -> lockTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList(
                    "SELECT id FROM location_inventory WHERE id = ? FOR UPDATE", inventory.getId());
            lockAcquired.countDown();
            try {
                if (!releaseLock.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release signal never arrived");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));

        assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                .as("the row lock must be acquired before the racing adjustments start")
                .isTrue();

        ExecutorService raceExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<List<StockMovement>> futureA = raceExecutor.submit(() -> stockMovementService.batchAdjustInventory(
                    singleLineAdjust(location.getId(), inventory.getId(), deltaA)));
            Future<List<StockMovement>> futureB = raceExecutor.submit(() -> stockMovementService.batchAdjustInventory(
                    singleLineAdjust(location.getId(), inventory.getId(), deltaB)));

            // Both calls' new id-ordered PESSIMISTIC_WRITE lock query blocks on the externally
            // held row lock -- this is the proof that both are genuinely concurrent, overlapping
            // transactions, not two sequential calls.
            awaitBackendsWaitingOnALock(2, 10_000);
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            List<StockMovement> resultA = futureA.get(10, TimeUnit.SECONDS);
            List<StockMovement> resultB = futureB.get(10, TimeUnit.SECONDS);

            assertThat(resultA).hasSize(1);
            assertThat(resultB).hasSize(1);

            LocationInventory finalInventory = locationInventoryRepository.findById(inventory.getId())
                    .orElseThrow();
            assertThat(finalInventory.getQuantity())
                    .as("the final quantity must equal the serialized result of both adjustments, "
                            + "not whichever write happened to land last on a stale read")
                    .isEqualTo(expectedFinalQuantity);

            List<StockMovement> movements = stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId());
            assertThat(movements)
                    .as("exactly one movement row per adjustment call, no duplicates and no lost writes")
                    .hasSize(2);

            List<StockMovement> chronological = movements.stream()
                    .sorted(Comparator.comparing(StockMovement::getAt))
                    .toList();

            for (StockMovement movement : chronological) {
                assertThat(movement.getPreviousQuantity())
                        .as("no movement may ever record a negative quantity mid-flight")
                        .isGreaterThanOrEqualTo(0);
                assertThat(movement.getCurrentQuantity())
                        .as("no movement may ever record a negative quantity mid-flight")
                        .isGreaterThanOrEqualTo(0);
            }

            // The two movements form one unbroken chain from 100 down to the final quantity,
            // in whichever order Postgres actually serialized the two transactions -- proving
            // the second writer really did see the first writer's committed result, not a
            // stale pre-lock value.
            StockMovement first = chronological.get(0);
            StockMovement second = chronological.get(1);
            assertThat(first.getPreviousQuantity()).isEqualTo(100);
            assertThat(second.getPreviousQuantity()).isEqualTo(first.getCurrentQuantity());
            assertThat(second.getCurrentQuantity()).isEqualTo(expectedFinalQuantity);
        } finally {
            raceExecutor.shutdownNow();
            lockExecutor.shutdownNow();
        }
    }
}
