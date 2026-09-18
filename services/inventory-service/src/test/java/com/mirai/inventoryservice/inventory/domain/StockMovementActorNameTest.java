package com.mirai.inventoryservice.inventory.domain;

import com.mirai.inventoryservice.models.audit.AuditLog;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * stock_movements.actor_id has no FK (dropped in V48 so users can be deleted), so a movement
 * whose actor row is gone is unattributable unless the name was captured at write time. The
 * name is inherited from the linked AuditLog in @PrePersist rather than at each of the ~31
 * StockMovement creation sites, so these cover that fallback directly.
 */
class StockMovementActorNameTest {

    @Test
    void prePersist_InheritsActorNameFromLinkedAuditLog() {
        AuditLog auditLog = AuditLog.builder().actorName("Dana Kim").build();
        StockMovement movement = StockMovement.builder().auditLog(auditLog).build();

        movement.prePersist();

        assertEquals("Dana Kim", movement.getActorName());
    }

    @Test
    void prePersist_KeepsExplicitActorNameOverAuditLog() {
        // Paths that persist a movement without a parent AuditLog set the name themselves;
        // the fallback must never overwrite it.
        AuditLog auditLog = AuditLog.builder().actorName("From Audit Log").build();
        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .actorName("Explicitly Set")
                .build();

        movement.prePersist();

        assertEquals("Explicitly Set", movement.getActorName());
    }

    @Test
    void prePersist_NoAuditLog_LeavesActorNameNull() {
        StockMovement movement = StockMovement.builder().build();

        movement.prePersist();

        assertNull(movement.getActorName());
    }

    @Test
    void prePersist_AuditLogWithoutActorName_LeavesActorNameNull() {
        // System-initiated movements have an audit log but no human actor.
        StockMovement movement = StockMovement.builder()
                .auditLog(AuditLog.builder().build())
                .build();

        movement.prePersist();

        assertNull(movement.getActorName());
    }

    @Test
    void prePersist_StillDefaultsTimestamp() {
        StockMovement movement = StockMovement.builder().build();

        movement.prePersist();

        assertNotNull(movement.getAt(), "existing timestamp defaulting must be preserved");
    }

    @Test
    void prePersist_DoesNotOverwriteExplicitTimestamp() {
        OffsetDateTime at = OffsetDateTime.now().minusDays(3);
        StockMovement movement = StockMovement.builder().at(at).build();

        movement.prePersist();

        assertEquals(at, movement.getAt());
    }
}
