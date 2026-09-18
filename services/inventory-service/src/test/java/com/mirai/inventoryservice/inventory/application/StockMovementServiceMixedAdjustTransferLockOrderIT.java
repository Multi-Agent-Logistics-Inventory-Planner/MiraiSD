package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.BatchAdjustLineDTO;
import com.mirai.inventoryservice.inventory.api.BatchAdjustStockRequestDTO;
import com.mirai.inventoryservice.inventory.api.BatchTransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.api.TransferInventoryRequestDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory/log.md, review-driven fix round 3: T-6c-6 P1 finding
 * ("adjustment and transfer batches use conflicting lock orders"). Before this fix,
 * {@link StockMovementService#batchAdjustInventory} locked its rows in ascending row-id order
 * ({@code lockAllByIdForUpdate}), while the transfer paths (round 2's redesign) locked in
 * {@code (location, product)} order -- two independently-consistent orderings that can still
 * disagree with EACH OTHER for two products at one location, since row id and product id are
 * unrelated random UUIDs. A batch-adjust and a concurrent batch-transfer that both touch the same
 * two rows could then acquire them in opposite relative order and deadlock -- reproduced by the
 * reviewer as a real {@code ERROR: deadlock detected} using the production repository lock
 * methods.
 * <p>
 * The fix removes the id-ordered lock path entirely: {@code batchAdjustInventory} now resolves
 * every requested inventory id to its {@code (location, product)} key and locks through the exact
 * same {@code ensureAndLockInventoryRow} routine the transfer paths use, in the same
 * {@code LOCATION_PRODUCT_KEY_ORDER}. There is now exactly one lock-ordering domain shared by
 * every writer that can hold more than one {@code LocationInventory} row in a transaction.
 * <p>
 * This test deliberately assigns the two destination rows' ids so that row-id order and
 * {@code (location, product)} order disagree (row-id order is the exact reverse of key order) --
 * not left to chance -- so the pre-fix scenario is reliably reproducible rather than depending on
 * whichever way two random UUIDs happen to compare. Real Postgres (Testcontainers), since this is
 * about genuine multi-transaction lock-ordering semantics.
 */
class StockMovementServiceMixedAdjustTransferLockOrderIT extends BaseKafkaIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

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

    private Product newProduct(String label) {
        String suffix = label + "-" + System.nanoTime();
        Category category = categoryRepository.save(Category.builder()
                .name("Mixed Lock Order IT Category " + suffix)
                .slug("mixed-lock-order-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("MIXED-LOCK-IT-" + suffix)
                .name("Mixed Lock Order IT Product " + suffix)
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
                .locationCode("MIXLOCK-" + suffix)
                .build());
    }

    /**
     * Inserts a committed {@code location_inventory} row with an explicitly chosen id, bypassing
     * Hibernate's {@code GenerationType.UUID} generator so the row id can be deliberately set
     * relative to the row's product id -- the mismatch this test needs to force is otherwise a
     * coin flip on two independently-random UUIDs.
     */
    private void insertRowWithExplicitId(UUID id, Location location, Product product, int quantity) {
        jdbcTemplate.update(
                "INSERT INTO location_inventory (id, location_id, site_id, product_id, quantity, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
                id, location.getId(), location.getStorageLocation().getSite().getId(), product.getId(), quantity);
    }

    @Test
    void concurrentBatchAdjustAndBatchTransferOnSameTwoRowsDoNotDeadlock() throws Exception {
        Product productA = newProduct("ProductA");
        Product productB = newProduct("ProductB");

        // Determine which product sorts first by (location, product) key order -- location is
        // shared, so this reduces to comparing product ids.
        boolean aSortsFirstByKey = productA.getId().compareTo(productB.getId()) < 0;
        Product keyFirstProduct = aSortsFirstByKey ? productA : productB;
        Product keySecondProduct = aSortsFirstByKey ? productB : productA;

        Location destination = newLocation("Dest");

        // Deliberately reversed: the product that sorts FIRST by (location, product) key gets the
        // LARGER row id, and the product that sorts SECOND gets the SMALLER row id -- so row-id
        // order is the exact opposite of key order, guaranteed regardless of the actual random
        // product ids.
        UUID keyFirstRowId = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffffe");
        UUID keySecondRowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        insertRowWithExplicitId(keyFirstRowId, destination, keyFirstProduct, 100);
        insertRowWithExplicitId(keySecondRowId, destination, keySecondProduct, 100);

        Location sourceForKeyFirst = newLocation("SrcKeyFirst");
        Location sourceForKeySecond = newLocation("SrcKeySecond");
        LocationInventory sourceKeyFirst = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceForKeyFirst)
                .site(sourceForKeyFirst.getStorageLocation().getSite())
                .product(keyFirstProduct)
                .quantity(50)
                .build());
        LocationInventory sourceKeySecond = locationInventoryRepository.save(LocationInventory.builder()
                .location(sourceForKeySecond)
                .site(sourceForKeySecond.getStorageLocation().getSite())
                .product(keySecondProduct)
                .quantity(50)
                .build());

        int adjustKeyFirst = -10;
        int adjustKeySecond = -7;
        int transferIntoKeyFirst = 5;
        int transferIntoKeySecond = 3;
        int expectedKeyFirst = 100 + adjustKeyFirst + transferIntoKeyFirst;
        int expectedKeySecond = 100 + adjustKeySecond + transferIntoKeySecond;

        BatchAdjustStockRequestDTO adjustBatch = BatchAdjustStockRequestDTO.builder()
                .locationType(LocationType.BOX_BIN)
                .locationId(destination.getId())
                .adjustments(List.of(
                        BatchAdjustLineDTO.builder().inventoryId(keyFirstRowId).quantityChange(adjustKeyFirst).build(),
                        BatchAdjustLineDTO.builder().inventoryId(keySecondRowId).quantityChange(adjustKeySecond).build()))
                .reason(StockMovementReason.ADJUSTMENT)
                .build();

        BatchTransferInventoryRequestDTO transferBatch = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(
                        TransferInventoryRequestDTO.builder()
                                .sourceLocationType(LocationType.BOX_BIN)
                                .sourceInventoryId(sourceKeyFirst.getId())
                                .destinationLocationType(LocationType.BOX_BIN)
                                .destinationLocationId(destination.getId())
                                .quantity(transferIntoKeyFirst)
                                .build(),
                        TransferInventoryRequestDTO.builder()
                                .sourceLocationType(LocationType.BOX_BIN)
                                .sourceInventoryId(sourceKeySecond.getId())
                                .destinationLocationType(LocationType.BOX_BIN)
                                .destinationLocationId(destination.getId())
                                .quantity(transferIntoKeySecond)
                                .build()))
                .build();

        // Force the exact interleaving a natural race cannot reliably reproduce: hold an external
        // lock on keySecondRowId, start the adjust batch (which, pre-fix, scans in ascending row
        // id -- keySecondRowId first -- and blocks immediately on this external hold before ever
        // touching keyFirstRowId), wait for it to actually block, THEN start the transfer batch
        // (which, being (location, product)-ordered, acquires keyFirstRowId first -- uncontended,
        // succeeds -- then also blocks on keySecondRowId). Releasing the external hold with the
        // adjust batch already queued first for keySecondRowId reproduces the classic crossed-wait:
        // adjust is granted keySecondRowId and then blocks on keyFirstRowId (held by transfer),
        // while transfer still holds keyFirstRowId and blocks on keySecondRowId (now held by
        // adjust) -- a genuine deadlock, not a timing coincidence.
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate lockTemplate = new TransactionTemplate(transactionManager);
        ExecutorService lockExecutor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = lockExecutor.submit(() -> lockTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList("SELECT id FROM location_inventory WHERE id = ? FOR UPDATE", keySecondRowId);
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
                .as("the row lock must be acquired before the racing operations start")
                .isTrue();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> adjustFuture = executor.submit(() -> {
                stockMovementService.batchAdjustInventory(adjustBatch);
                return null;
            });
            awaitBackendsWaitingOnALock(1, 10_000);

            Future<?> transferFuture = executor.submit(() -> {
                stockMovementService.batchTransferInventory(transferBatch);
                return null;
            });
            awaitBackendsWaitingOnALock(2, 10_000);

            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            // Both calls must complete without throwing. Pre-fix, batchAdjustInventory locked
            // [keySecondRowId, keyFirstRowId] (ascending row id) while batchTransferInventory
            // locked [keyFirstRowId, keySecondRowId] (ascending (location, product) key) -- the
            // forced interleaving above reproduces the crossed wait a real Postgres deadlock needs.
            adjustFuture.get(20, TimeUnit.SECONDS);
            transferFuture.get(20, TimeUnit.SECONDS);

            LocationInventory keyFirstRow = locationInventoryRepository.findById(keyFirstRowId).orElseThrow();
            LocationInventory keySecondRow = locationInventoryRepository.findById(keySecondRowId).orElseThrow();
            assertThat(keyFirstRow.getQuantity())
                    .as("key-first row must reflect both the adjustment and the transfer, no lost update")
                    .isEqualTo(expectedKeyFirst);
            assertThat(keySecondRow.getQuantity())
                    .as("key-second row must reflect both the adjustment and the transfer, no lost update")
                    .isEqualTo(expectedKeySecond);
        } finally {
            executor.shutdownNow();
            lockExecutor.shutdownNow();
        }
    }
}
