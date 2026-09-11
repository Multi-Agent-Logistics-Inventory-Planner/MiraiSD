package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.services.EventOutboxService;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * .specs/phase-6-inventory/log.md T-5: {@link InventoryOperations} is the narrow write facade
 * external-to-inventory production callers (ShipmentService, KujiBoxService, MachineDisplayService)
 * now use instead of {@code LocationInventoryRepository}/{@code StockMovementRepository} directly.
 * These tests pin the exact find-or-create/adjust/save-or-delete/record-movement/publish-outbox
 * sequence those callers relied on inline before the migration, so a regression here would be
 * silently visible only as a behavior change (wrong end-state or a missing/duplicated outbox
 * event) in one of its callers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryOperationsTest {

    @Mock private LocationInventoryRepository locationInventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private EventOutboxService eventOutboxService;
    @Mock private StockMovementService stockMovementService;

    private InventoryOperations inventoryOperations;

    private Product product;
    private Location location;
    private Site site;
    private UUID locationId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        inventoryOperations = new InventoryOperations(
                locationInventoryRepository, stockMovementRepository, eventOutboxService, stockMovementService);

        productId = UUID.randomUUID();
        product = new Product();
        product.setId(productId);
        product.setName("Widget");

        site = Site.builder().id(UUID.randomUUID()).code("MAIN").name("Main").build();
        StorageLocation storageLocation = StorageLocation.builder()
                .id(UUID.randomUUID()).site(site).code("RACKS").name("Racks").build();
        locationId = UUID.randomUUID();
        location = Location.builder()
                .id(locationId).storageLocation(storageLocation).locationCode("R1").build();

        when(locationInventoryRepository.save(any(LocationInventory.class)))
                .thenAnswer(inv -> {
                    LocationInventory li = inv.getArgument(0);
                    if (li.getId() == null) {
                        li.setId(UUID.randomUUID());
                    }
                    return li;
                });
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ===================== adjustQuantity =====================

    @Test
    void adjustQuantity_createsRowWhenNoneExists() {
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                .thenReturn(Optional.empty());

        InventoryOperations.InventoryQuantityChange change =
                inventoryOperations.adjustQuantity(location, product, 5);

        assertEquals(0, change.previousQuantity());
        assertEquals(5, change.currentQuantity());
        ArgumentCaptor<LocationInventory> captor = ArgumentCaptor.forClass(LocationInventory.class);
        verify(locationInventoryRepository, times(1)).save(captor.capture());
        assertEquals(5, captor.getValue().getQuantity());
        assertEquals(locationId, captor.getValue().getLocation().getId());
    }

    @Test
    void adjustQuantity_updatesExistingRow() {
        LocationInventory existing = LocationInventory.builder()
                .id(UUID.randomUUID()).location(location).product(product).quantity(10).build();
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                .thenReturn(Optional.of(existing));

        InventoryOperations.InventoryQuantityChange change =
                inventoryOperations.adjustQuantity(location, product, -4);

        assertEquals(10, change.previousQuantity());
        assertEquals(6, change.currentQuantity());
        verify(locationInventoryRepository, times(1)).save(existing);
        verify(locationInventoryRepository, never()).delete(any());
    }

    @Test
    void adjustQuantity_deletesRowWhenResultIsZero() {
        UUID existingId = UUID.randomUUID();
        LocationInventory existing = LocationInventory.builder()
                .id(existingId).location(location).product(product).quantity(3).build();
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                .thenReturn(Optional.of(existing));

        InventoryOperations.InventoryQuantityChange change =
                inventoryOperations.adjustQuantity(location, product, -3);

        assertEquals(3, change.previousQuantity());
        assertEquals(0, change.currentQuantity());
        assertEquals(existingId, change.inventoryId());
        verify(locationInventoryRepository, times(1)).delete(existing);
        verify(locationInventoryRepository, never()).save(any());
    }

    // ===================== recordMovement =====================

    @Test
    void recordMovement_fieldForm_savesAndPublishesOutbox() {
        UUID auditLogId = UUID.randomUUID();
        var auditLog = com.mirai.inventoryservice.models.audit.AuditLog.builder().id(auditLogId).build();
        Map<String, Object> metadata = Map.of("k", "v");

        StockMovement saved = inventoryOperations.recordMovement(
                auditLog, product, LocationType.RACK, null, locationId,
                0, 5, 5, StockMovementReason.SHIPMENT_RECEIPT, UUID.randomUUID(), metadata,
                location.getStorageLocation().getSite());

        assertEquals(5, saved.getQuantityChange());
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(captor.capture());
        assertEquals(metadata, captor.getValue().getMetadata());
        assertEquals(locationId, captor.getValue().getToLocationId());
        assertNull(captor.getValue().getFromLocationId());
        assertEquals(location.getStorageLocation().getSite(), captor.getValue().getSite());
        verify(eventOutboxService, times(1)).createStockMovementEvent(saved);
    }

    @Test
    void recordMovement_prebuiltForm_savesAndPublishesOutbox() {
        StockMovement movement = StockMovement.builder()
                .item(product).locationType(LocationType.NOT_ASSIGNED)
                .previousQuantity(0).currentQuantity(0).quantityChange(0)
                .reason(StockMovementReason.KUJI_PRIZE_WON)
                .site(site)
                .build();

        StockMovement saved = inventoryOperations.recordMovement(movement);

        assertEquals(movement, saved);
        verify(stockMovementRepository, times(1)).save(movement);
        verify(eventOutboxService, times(1)).createStockMovementEvent(movement);
    }

    // ===================== applyDelta =====================

    @Test
    void applyDelta_positiveDelta_setsOnlyToLocation() {
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                .thenReturn(Optional.empty());

        StockMovement saved = inventoryOperations.applyDelta(
                location, product, 7, LocationType.RACK, null,
                StockMovementReason.SHIPMENT_RECEIPT, UUID.randomUUID(), Map.of());

        assertEquals(locationId, saved.getToLocationId());
        assertNull(saved.getFromLocationId());
        assertEquals(0, saved.getPreviousQuantity());
        assertEquals(7, saved.getCurrentQuantity());
        assertEquals(location.getStorageLocation().getSite(), saved.getSite());
        verify(eventOutboxService, times(1)).createStockMovementEvent(saved);
    }

    @Test
    void applyDelta_negativeDelta_setsOnlyFromLocation() {
        LocationInventory existing = LocationInventory.builder()
                .id(UUID.randomUUID()).location(location).product(product).quantity(8).build();
        when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                .thenReturn(Optional.of(existing));

        StockMovement saved = inventoryOperations.applyDelta(
                location, product, -8, LocationType.RACK, null,
                StockMovementReason.SHIPMENT_RECEIPT_REVERSED, UUID.randomUUID(), Map.of());

        assertEquals(locationId, saved.getFromLocationId());
        assertNull(saved.getToLocationId());
        assertEquals(8, saved.getPreviousQuantity());
        assertEquals(0, saved.getCurrentQuantity());
        assertEquals(location.getStorageLocation().getSite(), saved.getSite());
        verify(locationInventoryRepository, times(1)).delete(existing);
    }

    // ===================== saveMovement / saveMovements (no outbox) =====================

    @Test
    void saveMovement_savesWithoutPublishingOutbox() {
        StockMovement movement = StockMovement.builder()
                .item(product).previousQuantity(0).currentQuantity(0).quantityChange(0).site(site).build();

        StockMovement saved = inventoryOperations.saveMovement(movement);

        assertEquals(movement, saved);
        verify(stockMovementRepository, times(1)).save(movement);
        verify(eventOutboxService, never()).createStockMovementEvent(any());
    }

    @Test
    void saveMovements_batchSavesWithoutPublishingOutbox() {
        StockMovement m1 = StockMovement.builder().item(product).previousQuantity(0).currentQuantity(0).quantityChange(0).site(site).build();
        StockMovement m2 = StockMovement.builder().item(product).previousQuantity(0).currentQuantity(0).quantityChange(0).site(site).build();
        when(stockMovementRepository.saveAll(List.of(m1, m2))).thenReturn(List.of(m1, m2));

        List<StockMovement> saved = inventoryOperations.saveMovements(List.of(m1, m2));

        assertEquals(2, saved.size());
        verify(eventOutboxService, never()).createStockMovementEvent(any());
    }

    // ===================== syncProductTotals =====================

    @Test
    void syncProductTotals_delegatesToStockMovementService() {
        List<UUID> ids = List.of(productId);

        inventoryOperations.syncProductTotals(ids);

        verify(stockMovementService, times(1)).syncProductTotals(ids);
    }
}
