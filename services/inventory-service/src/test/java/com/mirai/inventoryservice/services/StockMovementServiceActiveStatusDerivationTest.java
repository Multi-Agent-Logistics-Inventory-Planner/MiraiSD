package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.application.CatalogQueries;
import com.mirai.inventoryservice.catalog.application.ProductRef;
import com.mirai.inventoryservice.catalog.application.ProductStockStateWriter;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.dtos.requests.BatchAdjustLineDTO;
import com.mirai.inventoryservice.dtos.requests.BatchAdjustStockRequestDTO;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.KujiBoxTierRepository;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-2b's required pinning test (docs: .specs/phase-5b-catalog-facade/spec.md): the
 * {@code shouldBeActive = total > 0} derivation must stay exactly where it lives today (in
 * {@code StockMovementService}, computed from a real inventory total) even though the actual
 * write now goes through {@link ProductStockStateWriter} — so that Phase 6's eventual change to
 * this formula (splitting {@code is_active}'s stock-derived meaning from its master-catalog
 * meaning, per spec.md's hazard note) shows up as a deliberate, visible diff to this test, not an
 * accidental behavior change slipping through T-4's facade migration unnoticed.
 *
 * <p>Covers three distinct derivation call sites, each through its real public entry point
 * rather than a private helper directly (private, and resilient to internal refactoring of
 * exactly how the helpers share logic) — {@code syncProductTotals} does <b>not</b> exercise
 * {@code updateProductActiveStatus}: it computes its own independent
 * {@code totalInventory}/{@code shouldBeActive} pair inline (needed because, unlike
 * {@code updateProductActiveStatus}'s three real callers, it only has product ids in hand, not
 * already-loaded {@code Product} entities). An earlier version of this class's javadoc claimed
 * otherwise; corrected here after review found {@code updateProductActiveStatus} itself — used by
 * {@code transferInventory}, {@code createInventoryWithTracking}, and
 * {@code removeInventoryWithTracking} — had no coverage at all.
 * <ul>
 *   <li>{@code syncProductTotals} → its own inline single-product derivation (the loop this
 *   method runs per id, distinct from {@code updateProductActiveStatus}).</li>
 *   <li>{@code removeInventoryWithTracking} → the actual {@code updateProductActiveStatus}
 *   helper (originally {@code StockMovementService.java:891-892}), exercised through one of its
 *   three real callers rather than assumed covered by a different method that merely looks
 *   similar.</li>
 *   <li>{@code batchAdjustInventory} → the batch path (originally
 *   {@code StockMovementService.java:363-364}, {@code applyProductActiveStatusFromTotals}) — a
 *   single-line batch (the "single adjustment" shape the batch-adjust endpoint now also serves,
 *   per its own javadoc: "single adjusts are now a batch of 1") and a genuinely multi-line batch,
 *   so the batch derivation is proven for both.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMovementServiceActiveStatusDerivationTest {

    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private CatalogQueries catalogQueries;
    @Mock private ProductStockStateWriter productStockStateWriter;
    @Mock private UserRepository userRepository;
    @Mock private LocationInventoryRepository locationInventoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StorageLocationRepository storageLocationRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private KujiBoxTierRepository kujiBoxTierRepository;
    @Mock private EntityManager entityManager;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private EventOutboxService eventOutboxService;

    private StockMovementService service;

    @BeforeEach
    void setUp() {
        service = new StockMovementService(
                stockMovementRepository, auditLogRepository, catalogQueries, productStockStateWriter,
                userRepository, locationInventoryRepository, locationRepository, storageLocationRepository,
                siteRepository, kujiBoxTierRepository, entityManager, broadcastService, eventOutboxService);
    }

    private ProductRef existingRef(UUID id) {
        return new ProductRef(id, "SKU", "Name", null, true, 0, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void zeroTotalInventory_derivesIsActiveFalse() {
        UUID productId = UUID.randomUUID();
        when(catalogQueries.findAllByIds(List.of(productId))).thenReturn(List.of(existingRef(productId)));
        when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(0);
        when(productStockStateWriter.applyStockState(eq(productId), eq(0), eq(false))).thenReturn(true);

        service.syncProductTotals(List.of(productId));

        verify(productStockStateWriter).applyStockState(productId, 0, false);
    }

    @Test
    void positiveTotalInventory_derivesIsActiveTrue() {
        UUID productId = UUID.randomUUID();
        when(catalogQueries.findAllByIds(List.of(productId))).thenReturn(List.of(existingRef(productId)));
        when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(7);
        when(productStockStateWriter.applyStockState(eq(productId), eq(7), eq(true))).thenReturn(true);

        service.syncProductTotals(List.of(productId));

        verify(productStockStateWriter).applyStockState(productId, 7, true);
    }

    @Test
    void nullSumFromRepository_treatedAsZero_derivesIsActiveFalse() {
        // sumQuantityByProductId returns null when a product has no location_inventory rows at
        // all (SQL SUM() with no matching rows) -- calculateTotalInventory's existing null-guard
        // is what makes that behave as "0 total", not a NullPointerException.
        UUID productId = UUID.randomUUID();
        when(catalogQueries.findAllByIds(List.of(productId))).thenReturn(List.of(existingRef(productId)));
        when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(null);
        when(productStockStateWriter.applyStockState(eq(productId), eq(0), eq(false))).thenReturn(true);

        service.syncProductTotals(List.of(productId));

        verify(productStockStateWriter).applyStockState(productId, 0, false);
    }

    // ---- updateProductActiveStatus, via removeInventoryWithTracking (a real caller) ----

    @Test
    void removeInventoryWithTracking_zeroRemainingTotal_derivesIsActiveFalse() {
        UUID inventoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocationInventory inventory = inventoryFixture(inventoryId, productId, 10);
        when(locationInventoryRepository.findById(inventoryId)).thenReturn(java.util.Optional.of(inventory));
        when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(0);
        when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeInventoryWithTracking(
                LocationType.BOX_BIN, inventoryId, StockMovementReason.REMOVED, null, null);

        verify(productStockStateWriter).applyStockState(productId, 0, false);
    }

    @Test
    void removeInventoryWithTracking_positiveRemainingTotal_derivesIsActiveTrue() {
        // Removing this one inventory row doesn't necessarily zero the product out -- it may
        // still have stock elsewhere, which is exactly why updateProductActiveStatus re-derives
        // the total from calculateTotalInventory rather than assuming "removed => inactive".
        UUID inventoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocationInventory inventory = inventoryFixture(inventoryId, productId, 10);
        when(locationInventoryRepository.findById(inventoryId)).thenReturn(java.util.Optional.of(inventory));
        when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(6);
        when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeInventoryWithTracking(
                LocationType.BOX_BIN, inventoryId, StockMovementReason.REMOVED, null, null);

        verify(productStockStateWriter).applyStockState(productId, 6, true);
    }

    // ---- batch path (applyProductActiveStatusFromTotals, via batchAdjustInventory) ----

    private LocationInventory inventoryFixture(UUID inventoryId, UUID productId, int quantity) {
        Site site = Site.builder().id(UUID.randomUUID()).code("MAIN").name("Main").build();
        StorageLocation storageLocation = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("BOX_BINS").name("Box Bins")
                .hasDisplay(false).isDisplayOnly(false).displayOrder(1).build();
        Location location = Location.builder()
                .id(UUID.randomUUID()).storageLocation(storageLocation).locationCode("B1").build();
        Category category = Category.builder().id(UUID.randomUUID()).name("Cat").slug("cat").build();
        Product product = Product.builder().id(productId).sku("SKU").name("Name").category(category).build();
        return LocationInventory.builder()
                .id(inventoryId).location(location).site(site).product(product).quantity(quantity)
                .build();
    }

    private BatchAdjustStockRequestDTO adjustRequest(UUID locationId, StockMovementReason reason,
                                                       List<BatchAdjustLineDTO> lines) {
        return BatchAdjustStockRequestDTO.builder()
                .locationType(LocationType.BOX_BIN)
                .locationId(locationId)
                .reason(reason)
                .adjustments(lines)
                .build();
    }

    /** {@code List.of(Object[]...)} would spread a single row's elements as top-level list
     * items instead of wrapping the row itself -- this keeps each {@code Object[]} row intact,
     * matching {@code sumQuantitiesByProductIds}'s real {@code List<Object[]>} shape. */
    private List<Object[]> rowsOf(Object[]... rows) {
        return List.of(rows);
    }

    private void stubCommonBatchCollaborators() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });
        when(stockMovementRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productStockStateWriter.applyStockStateBatch(any())).thenReturn(List.of());
    }

    @Test
    void batchAdjustInventory_singleLine_zeroResultingTotal_derivesIsActiveFalse() {
        UUID inventoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocationInventory inventory = inventoryFixture(inventoryId, productId, 10);
        when(locationInventoryRepository.findAllByIdWithGraph(any())).thenReturn(List.of(inventory));
        when(locationInventoryRepository.sumQuantitiesByProductIds(any()))
                .thenReturn(rowsOf(new Object[]{productId, 0L}));
        stubCommonBatchCollaborators();

        BatchAdjustStockRequestDTO request = adjustRequest(
                inventory.getLocation().getId(), StockMovementReason.SALE,
                List.of(BatchAdjustLineDTO.builder().inventoryId(inventoryId).quantityChange(-10).build()));

        service.batchAdjustInventory(request);

        verify(productStockStateWriter).applyStockStateBatch(
                Map.of(productId, new ProductStockStateWriter.StockState(0, false)));
    }

    @Test
    void batchAdjustInventory_singleLine_positiveResultingTotal_derivesIsActiveTrue() {
        UUID inventoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocationInventory inventory = inventoryFixture(inventoryId, productId, 10);
        when(locationInventoryRepository.findAllByIdWithGraph(any())).thenReturn(List.of(inventory));
        when(locationInventoryRepository.sumQuantitiesByProductIds(any()))
                .thenReturn(rowsOf(new Object[]{productId, 15L}));
        stubCommonBatchCollaborators();

        BatchAdjustStockRequestDTO request = adjustRequest(
                inventory.getLocation().getId(), StockMovementReason.RESTOCK,
                List.of(BatchAdjustLineDTO.builder().inventoryId(inventoryId).quantityChange(5).build()));

        service.batchAdjustInventory(request);

        verify(productStockStateWriter).applyStockStateBatch(
                Map.of(productId, new ProductStockStateWriter.StockState(15, true)));
    }

    @Test
    void batchAdjustInventory_multiLine_mixedZeroAndPositiveTotals_derivesEachProductIndependently() {
        UUID inventoryId1 = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID inventoryId2 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        LocationInventory inventory1 = inventoryFixture(inventoryId1, productId1, 10);
        LocationInventory inventory2 = inventoryFixture(inventoryId2, productId2, 3);
        // Both lines must share the same location for batchAdjustInventory's ownership check.
        inventory2.setLocation(inventory1.getLocation());

        when(locationInventoryRepository.findAllByIdWithGraph(any()))
                .thenReturn(List.of(inventory1, inventory2));
        when(locationInventoryRepository.sumQuantitiesByProductIds(any()))
                .thenReturn(rowsOf(
                        new Object[]{productId1, 0L},
                        new Object[]{productId2, 8L}));
        stubCommonBatchCollaborators();

        // Both lines subtract (same sign, satisfying batchAdjustInventory's mixed-sign guard) --
        // the resulting *totals* below are independently controlled by the mocked
        // sumQuantitiesByProductIds, not derived from these deltas, so one product still lands
        // at a zero total and the other at a positive one.
        BatchAdjustStockRequestDTO request = adjustRequest(
                inventory1.getLocation().getId(), StockMovementReason.SALE,
                List.of(
                        BatchAdjustLineDTO.builder().inventoryId(inventoryId1).quantityChange(-10).build(),
                        BatchAdjustLineDTO.builder().inventoryId(inventoryId2).quantityChange(-1).build()));

        service.batchAdjustInventory(request);

        verify(productStockStateWriter).applyStockStateBatch(Map.of(
                productId1, new ProductStockStateWriter.StockState(0, false),
                productId2, new ProductStockStateWriter.StockState(8, true)));
    }
}
