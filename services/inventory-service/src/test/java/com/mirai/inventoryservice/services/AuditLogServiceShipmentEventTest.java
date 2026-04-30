package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.mappers.AuditLogDTOMapper;
import com.mirai.inventoryservice.models.audit.AuditLog;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.AuditLogRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that the new createShipmentEvent path writes structured shipment columns
 * (shipment_id, shipment_number, field_changes, previous_status, new_status, override_reason)
 * onto the AuditLog entity instead of stuffing details into the notes blob.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceShipmentEventTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogDTOMapper auditLogMapper;
    @Mock private UserRepository userRepository;
    @Mock private SupabaseBroadcastService broadcastService;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(
                auditLogRepository, stockMovementRepository, auditLogMapper, userRepository, broadcastService);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void statusOverrideWritesStructuredColumns() {
        UUID shipmentId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        service.createShipmentEvent(
                actor, "Bob",
                StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN,
                shipmentId, "SHIP-123",
                1,
                null,
                "PENDING", "RECEIVED",
                "missing tracking; items received");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals(StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN, saved.getReason());
        assertEquals(shipmentId, saved.getShipmentId());
        assertEquals("SHIP-123", saved.getShipmentNumber());
        assertEquals("PENDING", saved.getPreviousStatus());
        assertEquals("RECEIVED", saved.getNewStatus());
        assertEquals("missing tracking; items received", saved.getOverrideReason());
        assertNull(saved.getFieldChanges(), "status override should not set field_changes");
        assertNull(saved.getNotes(), "structured columns replace prose notes");
        assertEquals("Shipment SHIP-123", saved.getProductSummary());
    }

    @Test
    void shipmentEditWritesFieldChangesArray() {
        UUID shipmentId = UUID.randomUUID();

        Map<String, Object> supplierChange = new HashMap<>();
        supplierChange.put("field", "supplier");
        supplierChange.put("from", "Acme");
        supplierChange.put("to", "Globex");

        Map<String, Object> statusChange = new HashMap<>();
        statusChange.put("field", "status");
        statusChange.put("from", "PENDING");
        statusChange.put("to", "RECEIVED");

        List<Map<String, Object>> fieldChanges = List.of(supplierChange, statusChange);

        service.createShipmentEvent(
                null, "Alice",
                StockMovementReason.SHIPMENT_EDITED,
                shipmentId, "SHIP-9",
                1,
                fieldChanges,
                null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals(StockMovementReason.SHIPMENT_EDITED, saved.getReason());
        assertEquals(shipmentId, saved.getShipmentId());
        assertEquals("SHIP-9", saved.getShipmentNumber());
        assertNotNull(saved.getFieldChanges());
        assertEquals(2, saved.getFieldChanges().size());
        assertEquals("supplier", saved.getFieldChanges().get(0).get("field"));
        assertEquals("status", saved.getFieldChanges().get(1).get("field"));
        assertNull(saved.getPreviousStatus(),
                "edit form changes go in field_changes, not the dedicated previous_status column");
        assertNull(saved.getNewStatus());
    }

    @Test
    void broadcastsAfterCreate() {
        service.createShipmentEvent(
                null, null,
                StockMovementReason.SHIPMENT_DELETED,
                UUID.randomUUID(), "SHIP-1",
                3,
                List.of(),
                null, null, null);
        verify(broadcastService).broadcastAuditLogCreated();
    }
}
