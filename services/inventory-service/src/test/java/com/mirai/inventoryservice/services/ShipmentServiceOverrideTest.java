package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidShipmentStatusException;
import com.mirai.inventoryservice.models.enums.ShipmentStatus;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.shipment.Shipment;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Focused tests for {@link ShipmentService#overrideShipmentStatus}.
 * Most of ShipmentService's collaborators are unused by the override path,
 * so we mock only what the method touches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentServiceOverrideTest {

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private com.mirai.inventoryservice.repositories.ShipmentItemRepository shipmentItemRepository;
    @Mock private com.mirai.inventoryservice.repositories.ProductRepository productRepository;
    @Mock private com.mirai.inventoryservice.services.ProductService productService;
    @Mock private com.mirai.inventoryservice.services.UserService userService;
    @Mock private com.mirai.inventoryservice.repositories.UserRepository userRepository;
    @Mock private com.mirai.inventoryservice.repositories.StockMovementRepository stockMovementRepository;
    @Mock private com.mirai.inventoryservice.repositories.LocationInventoryRepository locationInventoryRepository;
    @Mock private com.mirai.inventoryservice.repositories.LocationRepository locationRepository;
    @Mock private com.mirai.inventoryservice.repositories.StorageLocationRepository storageLocationRepository;
    @Mock private com.mirai.inventoryservice.repositories.SiteRepository siteRepository;
    @Mock private NotificationService notificationService;
    @Mock private StockMovementService stockMovementService;
    @Mock private AuditLogService auditLogService;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private EventOutboxService eventOutboxService;
    @Mock private SupplierService supplierService;

    private ShipmentService service;
    private UUID shipmentId;

    @BeforeEach
    void setUp() {
        service = new ShipmentService(
                shipmentRepository, shipmentItemRepository, productRepository, productService,
                userService, userRepository, stockMovementRepository, locationInventoryRepository,
                locationRepository, storageLocationRepository, siteRepository, notificationService,
                stockMovementService, auditLogService, broadcastService, eventOutboxService, supplierService);
        shipmentId = UUID.randomUUID();
    }

    private Shipment shipmentWith(ShipmentStatus status) {
        Shipment s = Shipment.builder()
                .id(shipmentId)
                .shipmentNumber("SHIP-1")
                .status(status)
                .build();
        when(shipmentRepository.findByIdWithAssociations(shipmentId)).thenReturn(Optional.of(s));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        return s;
    }

    @Test
    void overrideToReceivedSetsStatusAndAudits() {
        Shipment s = shipmentWith(ShipmentStatus.PENDING);
        UUID actor = UUID.randomUUID();

        Shipment result = service.overrideShipmentStatus(
                shipmentId, ShipmentStatus.RECEIVED, "missing tracking, items received", actor, "Bob");

        assertEquals(ShipmentStatus.RECEIVED, result.getStatus());
        verify(auditLogService).createShipmentEvent(
                eq(actor), eq("Bob"), eq(StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN),
                eq(shipmentId), eq("SHIP-1"), eq(1),
                isNull(),
                eq("PENDING"),
                eq("RECEIVED"),
                eq("missing tracking, items received"));
        // The legacy createAuditLog path is no longer called for overrides.
        verify(auditLogService, never()).createAuditLog(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any());
    }

    @Test
    void overrideTrimsReasonWhitespace() {
        shipmentWith(ShipmentStatus.PENDING);
        service.overrideShipmentStatus(
                shipmentId, ShipmentStatus.RECEIVED, "  padded reason  ", null, null);
        verify(auditLogService).createShipmentEvent(
                any(), any(), eq(StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN),
                any(), any(), anyInt(), any(), any(), any(),
                eq("padded reason"));
    }

    @Test
    void overrideRejectsCancelled() {
        // Don't stub shipmentRepository - the validation should reject before touching the DB
        assertThrows(InvalidShipmentStatusException.class, () ->
                service.overrideShipmentStatus(
                        shipmentId, ShipmentStatus.CANCELLED, "trying to cancel", null, null));
        verify(shipmentRepository, never()).save(any());
        verify(auditLogService, never()).createShipmentEvent(any(), any(), any(), any(), any(),
                anyInt(), any(), any(), any(), any());
    }

    @Test
    void overrideRequiresReason() {
        assertThrows(InvalidShipmentStatusException.class, () ->
                service.overrideShipmentStatus(
                        shipmentId, ShipmentStatus.RECEIVED, "", null, null));
        assertThrows(InvalidShipmentStatusException.class, () ->
                service.overrideShipmentStatus(
                        shipmentId, ShipmentStatus.RECEIVED, null, null, null));
    }

    @Test
    void overrideToSameStatusIsNoOp() {
        Shipment s = shipmentWith(ShipmentStatus.RECEIVED);
        service.overrideShipmentStatus(
                shipmentId, ShipmentStatus.RECEIVED, "no change", null, null);
        verify(shipmentRepository, never()).save(any());
        verify(auditLogService, never()).createShipmentEvent(any(), any(), any(), any(), any(),
                anyInt(), any(), any(), any(), any());
    }

    @Test
    void overrideHasNoInventorySideEffects() {
        shipmentWith(ShipmentStatus.PENDING);
        service.overrideShipmentStatus(
                shipmentId, ShipmentStatus.RECEIVED, "manual close-out", null, null);
        // No stock movement, no inventory mutation
        verify(stockMovementRepository, never()).save(any());
        verify(locationInventoryRepository, never()).save(any());
    }
}
