package com.mirai.inventoryservice.audit.application;

import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import com.mirai.inventoryservice.audit.api.AuditLogDTOMapper;
import com.mirai.inventoryservice.identity.application.UserService;
import com.mirai.inventoryservice.inventory.application.InventoryQueries;
import com.mirai.inventoryservice.audit.domain.AuditLog;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.audit.infrastructure.AuditLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceSiteTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private InventoryQueries inventoryQueries;
    @Mock private AuditLogDTOMapper auditLogMapper;
    @Mock private UserService userService;
    @Mock private SupabaseBroadcastService broadcastService;

    private AuditLogService service;
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuditLogService(
                auditLogRepository, inventoryQueries, auditLogMapper, userService, broadcastService);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAuditLogStampsTheSiteAndBroadcastsItScoped() {
        service.createAuditLog(siteId, null, "Bob", StockMovementReason.ADJUSTMENT,
                null, null, null, null, 1, 3, "summary", null);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertEquals(siteId, saved.getValue().getSiteId());
        verify(broadcastService).broadcastAuditLogCreated(siteId, null);
    }

    @Test
    void createAuditLogWithActorLookupStampsTheSite() {
        service.createAuditLog(siteId, UUID.randomUUID(), StockMovementReason.ADJUSTMENT,
                null, null, null, null, 1, 3, "summary", null);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertEquals(siteId, saved.getValue().getSiteId());
    }

    @Test
    void createShipmentEventStampsTheSiteAndBroadcastsItScoped() {
        service.createShipmentEvent(siteId, null, "Bob", StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN,
                UUID.randomUUID(), "SHIP-1", 1, null, "PENDING", "RECEIVED", "why");

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertEquals(siteId, saved.getValue().getSiteId());
        verify(broadcastService).broadcastAuditLogCreated(siteId, null);
    }

    @Test
    void detailLooksUpWithinTheCallersSiteOnly() {
        UUID id = UUID.randomUUID();
        when(auditLogRepository.findByIdAndSiteId(id, siteId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getAuditLogDetail(siteId, id));
        verify(auditLogRepository).findByIdAndSiteId(id, siteId);
    }

    @Test
    void detailFetchesMovementsWithinTheCallersSiteOnly() {
        UUID id = UUID.randomUUID();
        AuditLog log = AuditLog.builder().id(id).siteId(siteId).build();
        when(auditLogRepository.findByIdAndSiteId(id, siteId)).thenReturn(Optional.of(log));

        service.getAuditLogDetail(siteId, id);

        verify(inventoryQueries).findByAuditLogIdWithItem(id, siteId);
    }
}
