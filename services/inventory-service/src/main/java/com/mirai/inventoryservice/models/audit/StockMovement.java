package com.mirai.inventoryservice.models.audit;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@NamedEntityGraph(name = "StockMovement.withItem", attributeNodes = @NamedAttributeNode("item"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_log_id")
    private AuditLog auditLog;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @NotNull
    private Product item;

    @Column(name = "from_location_id")
    private UUID fromLocationId;

    @Column(name = "to_location_id")
    private UUID toLocationId;

    @Column(name = "previous_quantity")
    private Integer previousQuantity;

    @Column(name = "current_quantity")
    private Integer currentQuantity;

    @NotNull
    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementReason reason;

    @Column(name = "actor_id")
    private UUID actorId;

    /**
     * Actor's display name captured at write time. actor_id has no FK (V48 dropped it so users
     * can be deleted), so the UUID alone becomes unresolvable once the user row is gone. This
     * mirrors audit_logs.actor_name, and is populated automatically from the linked AuditLog in
     * {@link #prePersist()} rather than at each of the ~31 StockMovement creation sites.
     */
    @Column(name = "actor_name")
    private String actorName;

    @NotNull
    @Column(nullable = false)
    private OffsetDateTime at;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    /**
     * Package-private rather than private so the actor-name fallback can be unit tested without
     * a persistence context; JPA does not require any particular visibility here.
     */
    @PrePersist
    void prePersist() {
        if (at == null) {
            at = OffsetDateTime.now();
        }
        // AuditLogService always resolves actorName when it builds an AuditLog, so the linked
        // log is an in-memory source for the name - no extra query during flush.
        if (actorName == null && auditLog != null) {
            actorName = auditLog.getActorName();
        }
    }
}

