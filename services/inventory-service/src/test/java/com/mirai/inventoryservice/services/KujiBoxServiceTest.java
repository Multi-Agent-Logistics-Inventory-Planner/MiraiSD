package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.kuji.AddSlipRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.TransferInMoreRequestDTO;
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
                .count(INITIAL_SLIP_COUNT)
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
    void transferInInventoryOnly_incrementsBoxInventoryAndLeavesSlipsAlone() {
        service.transferInInventoryOnly(boxId, tierId, request());

        // Slip count is unchanged.
        assertEquals(INITIAL_SLIP_COUNT, tier.getCount(),
                "Slip count must not be modified by transferInInventoryOnly");
        // The tier itself is never saved (the method does not touch tier.count).
        verify(kujiBoxTierRepository, never()).save(any(KujiBoxTier.class));

        // A stock-movement pair was written and tagged with the new action.
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
        for (StockMovement mv : movementCaptor.getAllValues()) {
            Map<String, Object> meta = mv.getMetadata();
            assertNotNull(meta);
            assertEquals("transfer_in_inventory_only", meta.get("action"));
            assertEquals(boxId.toString(), meta.get("kuji_box_id"));
        }
        // Inventory rows were updated.
        verify(locationInventoryRepository, times(2)).save(any(LocationInventory.class));
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
        assertEquals(INITIAL_SLIP_COUNT, tier.getCount());
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
        assertEquals(INITIAL_SLIP_COUNT + 2, tier.getCount());

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
    void transferInMore_autoCreate_setsProductQuantityDirectlyAndSkipsSyncProductTotals() {
        // sourceLocationId == null routes through mintAutoCreatedAtBox. The auto-create
        // child only lives at the box, so Product.quantity is updated directly and
        // syncProductTotals is intentionally skipped.
        tier.setAutoCreatedProduct(true);
        linkedProduct.setQuantity(BOX_INVENTORY);
        linkedProduct.setIsActive(true);

        TransferInMoreRequestDTO req = new TransferInMoreRequestDTO();
        req.setActorId(actorId);
        req.setSourceLocationId(null); // triggers auto-create mint path
        req.setQuantity(TRANSFER_QUANTITY);

        when(locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                any(UUID.class), any(Collection.class)))
                .thenReturn(Collections.emptyList());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.transferInMore(boxId, tierId, req);

        // Product.quantity bumped to prev + transferred.
        assertEquals(BOX_INVENTORY + TRANSFER_QUANTITY, linkedProduct.getQuantity());
        // syncProductTotals NOT called — the denormalized total was already exact.
        verify(stockMovementService, never()).syncProductTotals(any());
        // Tier slip count was bumped (transferInMore semantics).
        assertEquals(INITIAL_SLIP_COUNT + TRANSFER_QUANTITY, tier.getCount());
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
                .count(2)
                .build();
        box.setTiers(new java.util.ArrayList<>(List.of(tier, secondTier)));

        when(locationInventoryRepository.findByLocation_IdAndProduct_IdIn(
                any(UUID.class), any(Collection.class)))
                .thenReturn(Collections.emptyList());

        // Reset to ignore findByLocation_IdAndProduct_Id stubs from setUp; we want to
        // assert the per-tier lookup is no longer invoked during DTO mapping.
        clearInvocations(locationInventoryRepository);

        service.addSlip(boxId, tierId, req);

        verify(locationInventoryRepository, times(1))
                .findByLocation_IdAndProduct_IdIn(eq(boxLocationId), any(Collection.class));
        verify(locationInventoryRepository, never())
                .findByLocation_IdAndProduct_Id(any(UUID.class), any(UUID.class));
    }

}
