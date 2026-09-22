package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.inventory.api.BatchTransferInventoryRequestDTO;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .specs/phase-6-inventory/log.md, review-driven fix round 2: T-6c-6 P1 findings, bug 2 ("batch
 * transfers can deadlock via speculative insertion"). Before this fix, {@code
 * resolveTransferLockIds}/{@code ensureDestinationInventoryExists} ran in request order -- whatever
 * order a batch's {@code transfers} list happened to list its lines in -- and ran before any
 * sorting applied. Two batches whose lines named the same two brand-new destination keys in
 * opposite order (batch 1: X then Y; batch 2: Y then X) could each successfully speculative-insert
 * their first key, then both block waiting for the other's speculative insert on the second key to
 * resolve -- a real Postgres deadlock, reproduced by the reviewer as an actual "PostgreSQL deadlock
 * detected" error.
 * <p>
 * The fix ({@code StockMovementService.planTransfers}/{@code lockPlannedRows}) collects every row
 * a whole batch will touch into one set, keyed by {@code (location, product)}, and locks/creates
 * them strictly in one global sorted order -- so two batches naming the same keys in opposite
 * request order still process them in the same relative order once sorted, eliminating the crossed
 * wait that causes the deadlock.
 */
class StockMovementServiceConcurrentBatchTransferCrossedDestinationsIT extends BaseKafkaIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private LocationInventoryRepository locationInventoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private DataSource dataSource;

    private Product newProduct(String label) {
        String suffix = label + "-" + System.nanoTime();
        Category category = categoryRepository.save(Category.builder()
                .name("Crossed Destinations IT Category " + suffix)
                .slug("crossed-destinations-it-category-" + suffix.toLowerCase())
                .build());
        return productRepository.save(Product.builder()
                .sku("CROSSED-DEST-IT-" + suffix)
                .name("Crossed Destinations IT Product " + suffix)
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
                .locationCode("CXBATCH-" + suffix)
                .build());
    }

    private LocationInventory newSource(String label, Product product, int quantity) {
        Location location = newLocation(label);
        return locationInventoryRepository.save(LocationInventory.builder()
                .location(location)
                .site(location.getStorageLocation().getSite())
                .product(product)
                .quantity(quantity)
                .build());
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

    /**
     * Proves both racing {@link StockMovementService#batchTransferInventory} calls complete
     * successfully -- no deadlock, no exception -- and both destination rows end up with the
     * correct summed quantities, regardless of which batch's rows a given assertion checks (both
     * should have applied cleanly). A {@link CyclicBarrier} forces both threads to invoke {@code
     * batchTransferInventory} at the same instant, maximizing genuine concurrent contention on the
     * shared destination keys; the fix's global (location, product)-sorted processing order is what
     * makes the outcome deadlock-free regardless of scheduling, not the timing of this barrier.
     */
    @Test
    void concurrentBatchTransfersNamingSameTwoNewDestinationsInOppositeOrderDoNotDeadlock() throws Exception {
        Product product = newProduct("Race");

        Location destinationX = newLocation("DestX");
        Location destinationY = newLocation("DestY");

        LocationInventory batch1SourceToX = newSource("B1SrcX", product, 100);
        LocationInventory batch1SourceToY = newSource("B1SrcY", product, 100);
        LocationInventory batch2SourceToY = newSource("B2SrcY", product, 100);
        LocationInventory batch2SourceToX = newSource("B2SrcX", product, 100);

        int batch1ToX = 10;
        int batch1ToY = 15;
        int batch2ToY = 5;
        int batch2ToX = 8;
        int expectedX = batch1ToX + batch2ToX;
        int expectedY = batch1ToY + batch2ToY;

        // Batch 1 names its lines in (X, Y) order; batch 2 names the same two destination keys in
        // (Y, X) order -- exactly the crossed request order the reviewer's finding described.
        BatchTransferInventoryRequestDTO batch1 = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(
                        transferTo(batch1SourceToX.getId(), destinationX.getId(), batch1ToX),
                        transferTo(batch1SourceToY.getId(), destinationY.getId(), batch1ToY)))
                .build();
        BatchTransferInventoryRequestDTO batch2 = BatchTransferInventoryRequestDTO.builder()
                .transfers(List.of(
                        transferTo(batch2SourceToY.getId(), destinationY.getId(), batch2ToY),
                        transferTo(batch2SourceToX.getId(), destinationX.getId(), batch2ToX)))
                .build();

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> future1 = executor.submit(() -> {
                startTogether.await();
                stockMovementService.batchTransferInventory(batch1);
                return null;
            });
            Future<?> future2 = executor.submit(() -> {
                startTogether.await();
                stockMovementService.batchTransferInventory(batch2);
                return null;
            });

            // Both calls must complete without throwing -- a deadlocked/aborted transaction here
            // is exactly the pre-fix failure mode this test exists to rule out.
            future1.get(20, TimeUnit.SECONDS);
            future2.get(20, TimeUnit.SECONDS);

            List<LocationInventory> xRows = locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                    destinationX.getId(), List.of(product.getId()));
            List<LocationInventory> yRows = locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                    destinationY.getId(), List.of(product.getId()));

            assertThat(xRows).as("exactly one row at destination X, never two from a lost creation race").hasSize(1);
            assertThat(yRows).as("exactly one row at destination Y, never two from a lost creation race").hasSize(1);
            assertThat(xRows.get(0).getQuantity())
                    .as("destination X must reflect both batches' contributions, no lost update")
                    .isEqualTo(expectedX);
            assertThat(yRows.get(0).getQuantity())
                    .as("destination Y must reflect both batches' contributions, no lost update")
                    .isEqualTo(expectedY);

            List<StockMovement> movements = stockMovementRepository.findByItem_IdOrderByAtDesc(product.getId());
            assertThat(movements)
                    .as("4 transfer lines total (2 per batch) x 2 movements each (withdrawal + deposit)")
                    .hasSize(8);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertNewKeyUncommitted(Connection conn, UUID locationId, UUID siteId, UUID productId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO location_inventory (id, location_id, site_id, product_id, quantity, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, 0, now(), now()) "
                        + "ON CONFLICT (location_id, product_id) DO NOTHING")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, locationId);
            ps.setObject(3, siteId);
            ps.setObject(4, productId);
            ps.executeUpdate();
        }
    }

    /**
     * Attempts the exact SQL-level pattern the pre-fix code's per-batch, request-order processing
     * would have executed for one side of a crossed-order race: speculatively insert this side's
     * first key, signal, wait for the other side to have done the same for ITS first key (which is
     * this side's second key), then attempt this side's second key. Returns "committed" on success
     * or the driver's error message (expected: a real Postgres deadlock message) on failure.
     */
    private String attemptCrossedOrderInsert(UUID firstLocationId, UUID secondLocationId, UUID siteId,
                                              UUID productId, CountDownLatch signalOwnFirstDone,
                                              CountDownLatch awaitOtherFirstDone) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            insertNewKeyUncommitted(conn, firstLocationId, siteId, productId);
            signalOwnFirstDone.countDown();
            if (!awaitOtherFirstDone.await(10, TimeUnit.SECONDS)) {
                conn.rollback();
                return "timed out waiting for the other side's first insert";
            }
            insertNewKeyUncommitted(conn, secondLocationId, siteId, productId);
            conn.commit();
            return "committed";
        } catch (SQLException e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    /**
     * Mechanism-level proof, independent of {@code StockMovementService}: two sessions
     * speculatively inserting the same two brand-new {@code (location, product)} keys in opposite
     * order genuinely deadlock in real Postgres. This is the literal SQL pattern the pre-fix,
     * request-ordered {@code resolveTransferLockIds}/{@code ensureDestinationInventoryExists} could
     * produce across two batches with crossed line orders -- confirming the reviewer's reported
     * "PostgreSQL deadlock detected" error is a real property of this statement shape, not an
     * artifact of a particular JVM thread schedule, and that the production fix's single global
     * sort order (proven not to deadlock in the test above) is addressing a genuine hazard.
     */
    @Test
    void crossedOrderSpeculativeInsertsOnTwoNewKeysGenuinelyDeadlockInPostgres() throws Exception {
        Product product = newProduct("Deadlock");
        Location locationX = newLocation("MechX");
        Location locationY = newLocation("MechY");
        UUID siteId = locationX.getStorageLocation().getSite().getId();

        CountDownLatch sideAInsertedFirst = new CountDownLatch(1);
        CountDownLatch sideBInsertedFirst = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> outcomeA = executor.submit(() -> attemptCrossedOrderInsert(
                    locationX.getId(), locationY.getId(), siteId, product.getId(),
                    sideAInsertedFirst, sideBInsertedFirst));
            Future<String> outcomeB = executor.submit(() -> attemptCrossedOrderInsert(
                    locationY.getId(), locationX.getId(), siteId, product.getId(),
                    sideBInsertedFirst, sideAInsertedFirst));

            String resultA = outcomeA.get(15, TimeUnit.SECONDS);
            String resultB = outcomeB.get(15, TimeUnit.SECONDS);

            assertThat(List.of(resultA, resultB))
                    .as("actual command outcomes: side A = [" + resultA + "], side B = [" + resultB + "]")
                    .anyMatch(outcome -> outcome != null && outcome.toLowerCase().contains("deadlock"));
        } finally {
            executor.shutdownNow();
        }
    }
}
