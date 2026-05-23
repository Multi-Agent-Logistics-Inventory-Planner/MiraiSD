package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.kuji.AddSlipRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.RecordDrawRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.TransferInMoreRequestDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiDailyPayoutsResponseDTO;
import com.mirai.inventoryservice.exceptions.InsufficientInventoryException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.kuji.KujiBox;
import com.mirai.inventoryservice.models.kuji.KujiBoxTier;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import com.mirai.inventoryservice.repositories.KujiBoxTierRepository;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KujiBoxService#transferInInventoryOnly}.
 * Verifies that the new action moves inventory into the box's location without
 * altering the tier's slip count, distinguishing it from the slip-coupled
 * {@link KujiBoxService#transferInMore}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KujiBoxServiceTest {

    @Mock private KujiBoxRepository kujiBoxRepository;
    @Mock private KujiBoxTierRepository kujiBoxTierRepository;
    @Mock private ProductRepository productRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private LocationInventoryRepository locationInventoryRepository;
    @Mock private MachineDisplayRepository machineDisplayRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private EventOutboxService eventOutboxService;
    @Mock private StockMovementService stockMovementService;
    @Mock private EntityManager entityManager;
    @Mock private ProductService productService;

    private KujiBoxService service;

    private UUID actorId;
    private UUID boxId;
    private UUID tierId;
    private UUID productId;
    private UUID sourceLocationId;
    private UUID boxLocationId;

    private KujiBox box;
    private KujiBoxTier tier;
    private Product linkedProduct;
    private Location sourceLocation;
    private Location boxLocation;

    private static final int INITIAL_SLIP_COUNT = 5;
    private static final int SOURCE_INVENTORY = 10;
    private static final int BOX_INVENTORY = 3;
    private static final int TRANSFER_QUANTITY = 4;

    @BeforeEach
    void setUp() {
        service = new KujiBoxService(
                kujiBoxRepository,
                kujiBoxTierRepository,
                productRepository,
                locationRepository,
                locationInventoryRepository,
                machineDisplayRepository,
                auditLogRepository,
                stockMovementRepository,
                userRepository,
                notificationService,
                broadcastService,
                eventOutboxService,
                stockMovementService,
                entityManager,
                productService);

        actorId = UUID.randomUUID();
        boxId = UUID.randomUUID();
        tierId = UUID.randomUUID();
        productId = UUID.randomUUID();
        sourceLocationId = UUID.randomUUID();
        boxLocationId = UUID.randomUUID();

        Site site = Site.builder().id(UUID.randomUUID()).code("MAIN").name("Main").build();
        StorageLocation sourceStorage = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("SRC").name("Source").build();
        StorageLocation boxStorage = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("BOX").name("Box").build();

        sourceLocation = Location.builder()
                .id(sourceLocationId).storageLocation(sourceStorage).locationCode("S1").build();
        boxLocation = Location.builder()
                .id(boxLocationId).storageLocation(boxStorage).locationCode("B1").build();

        linkedProduct = new Product();
        linkedProduct.setId(productId);
        linkedProduct.setName("Widget");

        Product parent = new Product();
        parent.setId(UUID.randomUUID());
        parent.setName("Kuji Parent");

        box = KujiBox.builder()
                .id(boxId)
                .product(parent)
                .location(boxLocation)
                .status(KujiBoxStatus.OPEN)
                .build();

        tier = KujiBoxTier.builder()
                .id(tierId)
                .box(box)
                .label("Tier A")
                .linkedProduct(linkedProduct)
                .activeCount(INITIAL_SLIP_COUNT)
                .build();

        when(kujiBoxRepository.findByIdWithTiers(boxId)).thenReturn(Optional.of(box));
        when(kujiBoxTierRepository.findByIdForUpdate(tierId)).thenReturn(Optional.of(tier));

        LocationInventory sourceInv = LocationInventory.builder()
                .id(UUID.randomUUID())
                .location(sourceLocation)
                .site(site)
                .product(linkedProduct)
                .quantity(SOURCE_INVENTORY)
                .build();
        LocationInventory boxInv = LocationInventory.builder()
                .id(UUID.randomUUID())
                .location(boxLocation)
                .site(site)
                .product(linkedProduct)
                .quantity(BOX_INVENTORY)
                .build();
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(sourceLocationId, productId))
                .thenReturn(Optional.of(sourceInv));
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(boxLocationId, productId))
                .thenReturn(Optional.of(boxInv));

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TransferInMoreRequestDTO request() {
        TransferInMoreRequestDTO req = new TransferInMoreRequestDTO();
        req.setActorId(actorId);
        req.setSourceLocationId(sourceLocationId);
        req.setQuantity(TRANSFER_QUANTITY);
        return req;
    }

    @Test
    void transferInInventoryOnly_addsToInactiveBucketAndDecrementsSource() {
        service.transferInInventoryOnly(boxId, tierId, request());

        // Active count unchanged; inactive bumped (transfer-in-inventory-only is the
        // "bring held stock into the kuji without binding it to slips" path).
        assertEquals(INITIAL_SLIP_COUNT, tier.getActiveCount(),
                "Active count must not change");
        assertEquals(TRANSFER_QUANTITY,
                tier.getInactiveCount() == null ? 0 : tier.getInactiveCount(),
                "Inactive count must be incremented by TRANSFER_QUANTITY");
        verify(kujiBoxTierRepository, atLeastOnce()).save(any(KujiBoxTier.class));

        // A single REMOVED stock-movement on the source side (no deposit at the machine).
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(movementCaptor.capture());
        StockMovement mv = movementCaptor.getValue();
        Map<String, Object> meta = mv.getMetadata();
        assertNotNull(meta);
        assertEquals("transfer_in_inventory_only", meta.get("action"));
        assertEquals(boxId.toString(), meta.get("kuji_box_id"));

        // Only the source LocationInventory row is touched.
        verify(locationInventoryRepository, times(1)).save(any(LocationInventory.class));
    }

    @Test
    void transferInInventoryOnly_rejectsWhenBoxClosed() {
        box.setStatus(KujiBoxStatus.CLOSED);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.transferInInventoryOnly(boxId, tierId, request()));
        assertTrue(ex.getMessage().contains("not OPEN"));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void transferInInventoryOnly_rejectsWhenTierBelongsToDifferentBox() {
        KujiBox otherBox = KujiBox.builder().id(UUID.randomUUID()).build();
        tier.setBox(otherBox);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.transferInInventoryOnly(boxId, tierId, request()));
        assertTrue(ex.getMessage().contains("does not belong"));
    }

    @Test
    void transferInInventoryOnly_rejectsWhenTierHasNoLinkedProduct() {
        tier.setLinkedProduct(null);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.transferInInventoryOnly(boxId, tierId, request()));
        assertTrue(ex.getMessage().contains("no linked product"));
    }

    @Test
    void transferInInventoryOnly_propagatesInsufficientInventory() {
        TransferInMoreRequestDTO req = request();
        req.setQuantity(SOURCE_INVENTORY + 1); // more than source has
        assertThrows(
                InsufficientInventoryException.class,
                () -> service.transferInInventoryOnly(boxId, tierId, req));
        // No movements written when the transfer is rejected.
        verify(stockMovementRepository, never()).save(any());
        assertEquals(INITIAL_SLIP_COUNT, tier.getActiveCount());
    }

    // ===================== Perf-optimization regression guards =====================

    @Test
    void addSlip_writesAuditLogOnlyAndNoStockMovementOrOutbox() {
        // Slip adjustments don't change inventory; the row used to be a no-op
        // StockMovement (qty=0) plus an outbox event. Both were dropped.
        AddSlipRequestDTO req = new AddSlipRequestDTO();
        req.setActorId(actorId);
        req.setQuantity(2);

        // toResponseDTO will batch-fetch tier inventory; provide an empty result.
        when(locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                any(UUID.class), any(Collection.class)))
                .thenReturn(Collections.emptyList());

        service.addSlip(boxId, tierId, req);

        // Tier slip count incremented.
        assertEquals(INITIAL_SLIP_COUNT + 2, tier.getActiveCount());

        // One AuditLog with KUJI_SLIP_ADJUSTMENT reason — and nothing else.
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(auditCaptor.capture());
        assertEquals(StockMovementReason.KUJI_SLIP_ADJUSTMENT, auditCaptor.getValue().getReason());

        // No StockMovement row. No outbox event.
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
        verify(eventOutboxService, never()).createStockMovementEvent(any());

        // No costly product-totals SUM either.
        verify(stockMovementService, never()).syncProductTotals(any());

        // Box not reloaded — toResponseDTO should run on the in-memory box.
        verify(kujiBoxRepository, times(1)).findByIdWithTiers(boxId);
        verify(entityManager).flush();
    }

    @Test
    void transferInMore_autoCreate_bumpsActiveCountWithoutTouchingProductOrInventory() {
        // sourceLocationId == null = the auto-create mint path. In the decoupled model,
        // auto-created prizes never live in location_inventory and Product.quantity is
        // not used as a counter; the tier's activeCount is the source of truth.
        tier.setAutoCreatedProduct(true);
        linkedProduct.setQuantity(BOX_INVENTORY);
        linkedProduct.setIsActive(true);

        TransferInMoreRequestDTO req = new TransferInMoreRequestDTO();
        req.setActorId(actorId);
        req.setSourceLocationId(null); // triggers auto-create branch (no source removal)
        req.setQuantity(TRANSFER_QUANTITY);

        service.transferInMore(boxId, tierId, req);

        // Product.quantity stays put — the kuji counter is the truth.
        assertEquals(BOX_INVENTORY, linkedProduct.getQuantity());
        // syncProductTotals NOT called — nothing in location_inventory changed.
        verify(stockMovementService, never()).syncProductTotals(any());
        // Tier active slip count was bumped.
        assertEquals(INITIAL_SLIP_COUNT + TRANSFER_QUANTITY, tier.getActiveCount());
    }

    @Test
    void toResponseDTO_batchesTierInventoryLookupIntoSingleQuery() {
        // Verifies the N+1 fix: regardless of tier count, exactly one batched
        // findByLocation_IdAndProduct_IdIn query and zero per-tier
        // findByLocation_IdAndProduct_Id calls during DTO mapping.
        AddSlipRequestDTO req = new AddSlipRequestDTO();
        req.setActorId(actorId);
        req.setQuantity(1);

        // Add a second tier with another linked product so N>1.
        Product otherProduct = new Product();
        otherProduct.setId(UUID.randomUUID());
        otherProduct.setName("Widget B");
        KujiBoxTier secondTier = KujiBoxTier.builder()
                .id(UUID.randomUUID())
                .box(box)
                .label("Tier B")
                .linkedProduct(otherProduct)
                .activeCount(2)
                .build();
        box.setTiers(new java.util.ArrayList<>(List.of(tier, secondTier)));

        // Reset to ignore findByLocation_IdAndProduct_Id stubs from setUp; we want to
        // assert that DTO mapping does NOT hit LocationInventory anymore — kuji prize
        // counts live on the tier, not on location_inventory.
        clearInvocations(locationInventoryRepository);

        service.addSlip(boxId, tierId, req);

        verify(locationInventoryRepository, never())
                .findByLocation_IdAndProduct_IdIn(any(UUID.class), any(Collection.class));
        verify(locationInventoryRepository, never())
                .findByLocation_IdAndProduct_Id(any(UUID.class), any(UUID.class));
    }

    // ===================== drawnCount =====================

    @Test
    void recordDraw_incrementsDrawnCountByDrawnQuantity() {
        tier.setDrawnCount(0);
        RecordDrawRequestDTO req = RecordDrawRequestDTO.builder()
                .actorId(actorId)
                .draws(List.of(RecordDrawRequestDTO.DrawLine.builder()
                        .tierId(tierId).quantity(3).build()))
                .build();

        service.recordDraw(boxId, req);

        assertEquals(INITIAL_SLIP_COUNT - 3, tier.getActiveCount(),
                "activeCount must decrement by drawn quantity");
        assertEquals(3, tier.getDrawnCount(),
                "drawnCount must increment by drawn quantity");
    }

    @Test
    void undoDraw_decrementsDrawnCountAndClampsAtZero() {
        // Set up: one prior draw of 2 slips already recorded.
        tier.setActiveCount(INITIAL_SLIP_COUNT - 2);
        tier.setDrawnCount(2);

        UUID auditLogId = UUID.randomUUID();
        AuditLog drawLog = AuditLog.builder()
                .id(auditLogId)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .build();
        when(auditLogRepository.findById(auditLogId)).thenReturn(Optional.of(drawLog));

        // findKujiReversalsForAuditLog uses a native query through entityManager.
        jakarta.persistence.Query nativeQuery = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(any(String.class), eq(StockMovement.class)))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        // Original draw movement carrying slip_quantity=2 in metadata.
        Map<String, Object> originalMeta = new java.util.HashMap<>();
        originalMeta.put("kuji_box_id", boxId.toString());
        originalMeta.put("kuji_box_tier_id", tierId.toString());
        originalMeta.put("slip_quantity", 2);
        StockMovement original = StockMovement.builder()
                .id(42L)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .quantityChange(0)
                .item(linkedProduct)
                .metadata(originalMeta)
                .build();
        when(stockMovementRepository.findByAuditLogIdWithItem(auditLogId))
                .thenReturn(List.of(original));

        service.undoDraw(boxId, auditLogId, actorId);

        assertEquals(INITIAL_SLIP_COUNT, tier.getActiveCount(),
                "activeCount must be restored to pre-draw value");
        assertEquals(0, tier.getDrawnCount(),
                "drawnCount must decrement and clamp at 0");
    }

    // ===================== daily-payouts =====================

    @Test
    void getDailyPayouts_rejectsInvalidTimezone() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.getDailyPayouts(boxId, null, null, "Not/A_Real_Zone"));
        assertTrue(ex.getMessage().contains("Invalid timezone"));
    }

    // ===================== unit_value snapshot =====================

    @Test
    void recordDraw_snapshotsTierPriceAsUnitValueInMetadata() {
        tier.setPrice(new java.math.BigDecimal("50.00"));
        RecordDrawRequestDTO req = RecordDrawRequestDTO.builder()
                .actorId(actorId)
                .draws(List.of(RecordDrawRequestDTO.DrawLine.builder()
                        .tierId(tierId).quantity(1).build()))
                .build();

        service.recordDraw(boxId, req);

        StockMovement saved = captureSavedDrawMovement();
        java.math.BigDecimal unitValue = (java.math.BigDecimal) saved.getMetadata().get("unit_value");
        assertNotNull(unitValue, "unit_value must be stamped into metadata");
        assertEquals(0, new java.math.BigDecimal("50.00").compareTo(unitValue),
                "unit_value should equal tier.price at draw time");
    }

    @Test
    void recordDraw_fallsBackToLinkedProductMsrpWhenTierPriceIsNull() {
        tier.setPrice(null);
        linkedProduct.setMsrp(new java.math.BigDecimal("30.00"));
        RecordDrawRequestDTO req = RecordDrawRequestDTO.builder()
                .actorId(actorId)
                .draws(List.of(RecordDrawRequestDTO.DrawLine.builder()
                        .tierId(tierId).quantity(1).build()))
                .build();

        service.recordDraw(boxId, req);

        StockMovement saved = captureSavedDrawMovement();
        java.math.BigDecimal unitValue = (java.math.BigDecimal) saved.getMetadata().get("unit_value");
        assertNotNull(unitValue);
        assertEquals(0, new java.math.BigDecimal("30.00").compareTo(unitValue),
                "unit_value should fall back to linked product MSRP when tier price is null");
    }

    @Test
    void recordDraw_locksInZeroWhenNeitherTierPriceNorMsrpSet() {
        tier.setPrice(null);
        linkedProduct.setMsrp(null);
        RecordDrawRequestDTO req = RecordDrawRequestDTO.builder()
                .actorId(actorId)
                .draws(List.of(RecordDrawRequestDTO.DrawLine.builder()
                        .tierId(tierId).quantity(1).build()))
                .build();

        service.recordDraw(boxId, req);

        StockMovement saved = captureSavedDrawMovement();
        assertTrue(saved.getMetadata().containsKey("unit_value"),
                "unit_value key must be present even when value is 0");
        java.math.BigDecimal unitValue = (java.math.BigDecimal) saved.getMetadata().get("unit_value");
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(unitValue),
                "unit_value should lock in 0 when no price is configured");
    }

    @Test
    void undoDraw_copiesSnapshotUnitValueIgnoringLiveTierPrice() {
        tier.setActiveCount(INITIAL_SLIP_COUNT - 1);
        tier.setDrawnCount(1);
        // Live tier price has drifted since the draw — undo must use the snapshot.
        tier.setPrice(new java.math.BigDecimal("80.00"));

        UUID auditLogId = UUID.randomUUID();
        AuditLog drawLog = AuditLog.builder()
                .id(auditLogId)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .build();
        when(auditLogRepository.findById(auditLogId)).thenReturn(Optional.of(drawLog));

        jakarta.persistence.Query nativeQuery = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(any(String.class), eq(StockMovement.class)))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        Map<String, Object> originalMeta = new java.util.HashMap<>();
        originalMeta.put("kuji_box_id", boxId.toString());
        originalMeta.put("kuji_box_tier_id", tierId.toString());
        originalMeta.put("slip_quantity", 1);
        originalMeta.put("unit_value", new java.math.BigDecimal("50.00"));
        StockMovement original = StockMovement.builder()
                .id(42L)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .quantityChange(0)
                .item(linkedProduct)
                .metadata(originalMeta)
                .build();
        when(stockMovementRepository.findByAuditLogIdWithItem(auditLogId))
                .thenReturn(List.of(original));

        service.undoDraw(boxId, auditLogId, actorId);

        StockMovement reversal = captureSavedMovementByReason(StockMovementReason.KUJI_DRAW_REVERSED);
        java.math.BigDecimal unitValue = (java.math.BigDecimal) reversal.getMetadata().get("unit_value");
        assertNotNull(unitValue, "reversal must carry the original snapshot");
        assertEquals(0, new java.math.BigDecimal("50.00").compareTo(unitValue),
                "reversal unit_value must mirror the draw snapshot, not the mutated live price");
    }

    @Test
    void undoDraw_legacyOriginalWithoutSnapshotOmitsUnitValueOnReversal() {
        tier.setActiveCount(INITIAL_SLIP_COUNT - 1);
        tier.setDrawnCount(1);

        UUID auditLogId = UUID.randomUUID();
        AuditLog drawLog = AuditLog.builder()
                .id(auditLogId)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .build();
        when(auditLogRepository.findById(auditLogId)).thenReturn(Optional.of(drawLog));

        jakarta.persistence.Query nativeQuery = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(any(String.class), eq(StockMovement.class)))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(String.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        Map<String, Object> originalMeta = new java.util.HashMap<>();
        originalMeta.put("kuji_box_id", boxId.toString());
        originalMeta.put("kuji_box_tier_id", tierId.toString());
        originalMeta.put("slip_quantity", 1);
        // No unit_value key — simulates a legacy draw recorded before this feature.
        StockMovement original = StockMovement.builder()
                .id(43L)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .quantityChange(0)
                .item(linkedProduct)
                .metadata(originalMeta)
                .build();
        when(stockMovementRepository.findByAuditLogIdWithItem(auditLogId))
                .thenReturn(List.of(original));

        service.undoDraw(boxId, auditLogId, actorId);

        StockMovement reversal = captureSavedMovementByReason(StockMovementReason.KUJI_DRAW_REVERSED);
        assertFalse(reversal.getMetadata().containsKey("unit_value"),
                "legacy original without snapshot must leave reversal metadata unit_value-free; "
                        + "aggregation SQL falls back to the live join via COALESCE");
    }

    private StockMovement captureSavedDrawMovement() {
        return captureSavedMovementByReason(StockMovementReason.KUJI_PRIZE_WON);
    }

    private StockMovement captureSavedMovementByReason(StockMovementReason reason) {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(m -> m.getReason() == reason)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No saved StockMovement with reason " + reason));
    }

    @Test
    void getDailyPayouts_padsDenseSeriesWithZeros() {
        box.setOpenedAt(java.time.OffsetDateTime.now().minusDays(2));
        when(kujiBoxRepository.findById(boxId)).thenReturn(Optional.of(box));

        // Only one row from the aggregator: 5 slips at $10 yesterday.
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("UTC"));
        java.time.LocalDate yesterday = today.minusDays(1);
        Object[] row = new Object[] {
                java.sql.Date.valueOf(yesterday),
                Integer.valueOf(5),
                new java.math.BigDecimal("50.00")
        };
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(row);
        when(stockMovementRepository.aggregateKujiDailyPayouts(
                eq(boxId), any(), any(), eq("UTC")))
                .thenReturn(rows);

        KujiDailyPayoutsResponseDTO resp = service.getDailyPayouts(
                boxId, today.minusDays(2), today, "UTC");

        assertEquals(3, resp.series().size(), "Series must be dense over [from, to]");
        assertEquals(0, resp.series().get(0).slipCount());
        assertEquals(5, resp.series().get(1).slipCount());
        assertEquals(0, resp.series().get(2).slipCount());
        assertEquals(0, new java.math.BigDecimal("50.00").compareTo(resp.total().valueWon()));
        assertEquals(5, resp.total().slipCount());
    }
}
