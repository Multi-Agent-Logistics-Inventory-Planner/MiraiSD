package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * .specs/phase-6-inventory 6e, T-6e-be-4/T-6e-be-6: the broadcast payload envelope (siteId +
 * productIds) and the after-commit dispatch guarantee, unit-tested at the payload-assembly and
 * self-invocation level (no HTTP, no real transaction manager).
 */
class SupabaseBroadcastServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // --- Payload assembly (T-6e-be-4) --------------------------------------------------------

    @Test
    void buildInventoryUpdatedPayload_includesSiteIdAndProductIds_whenPresent() {
        SupabaseBroadcastService service = new SupabaseBroadcastService(null);
        UUID siteId = UUID.randomUUID();

        ObjectNode payload = service.buildInventoryUpdatedPayload(
                siteId, "RACKS", List.of("p1", "p2"), "p1");

        assertThat(payload.get("type").asText()).isEqualTo("inventory_updated");
        assertThat(payload.get("siteId").asText()).isEqualTo(siteId.toString());
        assertThat(payload.get("locationType").asText()).isEqualTo("RACKS");
        assertThat(payload.get("itemId").asText()).isEqualTo("p1");
        assertThat(payload.get("productIds").isArray()).isTrue();
        assertThat(payload.get("productIds").size()).isEqualTo(2);
    }

    @Test
    void buildInventoryUpdatedPayload_omitsFields_whenNullOrEmpty() {
        SupabaseBroadcastService service = new SupabaseBroadcastService(null);

        ObjectNode payload = service.buildInventoryUpdatedPayload(null, null, List.of(), null);

        assertThat(payload.has("siteId")).isFalse();
        assertThat(payload.has("locationType")).isFalse();
        assertThat(payload.has("itemId")).isFalse();
        assertThat(payload.has("productIds")).isFalse();
    }

    @Test
    void buildAuditLogCreatedPayload_includesSiteId_whenPresent() {
        SupabaseBroadcastService service = new SupabaseBroadcastService(null);
        UUID siteId = UUID.randomUUID();

        ObjectNode payload = service.buildAuditLogCreatedPayload(siteId, "item-1");

        assertThat(payload.get("type").asText()).isEqualTo("audit_log_created");
        assertThat(payload.get("siteId").asText()).isEqualTo(siteId.toString());
        assertThat(payload.get("itemId").asText()).isEqualTo("item-1");
    }

    @Test
    void buildAuditLogCreatedPayload_omitsSiteId_whenNull() {
        SupabaseBroadcastService service = new SupabaseBroadcastService(null);

        ObjectNode payload = service.buildAuditLogCreatedPayload(null, null);

        assertThat(payload.has("siteId")).isFalse();
        assertThat(payload.has("itemId")).isFalse();
    }

    // --- After-commit dispatch (T-6e-be-6) ---------------------------------------------------

    @Test
    void broadcastNotificationCreated_noActiveTransaction_dispatchesImmediately() {
        SupabaseBroadcastService self = mock(SupabaseBroadcastService.class);
        SupabaseBroadcastService service = new SupabaseBroadcastService(self);

        service.broadcastNotificationCreated();

        verify(self, times(1)).dispatchNotificationCreated();
    }

    @Test
    void broadcastNotificationCreated_activeTransaction_defersUntilAfterCommit() {
        SupabaseBroadcastService self = mock(SupabaseBroadcastService.class);
        SupabaseBroadcastService service = new SupabaseBroadcastService(self);

        TransactionSynchronizationManager.initSynchronization();
        service.broadcastNotificationCreated();
        verify(self, never()).dispatchNotificationCreated();

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(self, times(1)).dispatchNotificationCreated();
    }

    @Test
    void broadcastNotificationCreated_transactionRolledBackWithoutCommit_neverDispatches() {
        SupabaseBroadcastService self = mock(SupabaseBroadcastService.class);
        SupabaseBroadcastService service = new SupabaseBroadcastService(self);

        TransactionSynchronizationManager.initSynchronization();
        service.broadcastNotificationCreated();
        // Simulate rollback: the transaction manager clears synchronizations without ever
        // invoking afterCommit() on them.
        TransactionSynchronizationManager.clearSynchronization();

        verify(self, never()).dispatchNotificationCreated();
    }

    @Test
    void broadcastInventoryUpdated_siteScoped_activeTransaction_defersAndCarriesArgs() {
        SupabaseBroadcastService self = mock(SupabaseBroadcastService.class);
        SupabaseBroadcastService service = new SupabaseBroadcastService(self);
        UUID siteId = UUID.randomUUID();

        TransactionSynchronizationManager.initSynchronization();
        service.broadcastInventoryUpdated(siteId, "RACKS", List.of("p1"), null);
        verify(self, never()).dispatchInventoryUpdated(siteId, "RACKS", List.of("p1"), null);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(self, times(1)).dispatchInventoryUpdated(siteId, "RACKS", List.of("p1"), null);
    }
}
