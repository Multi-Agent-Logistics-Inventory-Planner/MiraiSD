package com.mirai.inventoryservice.models.audit;

import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementReason reason;

    @Column(name = "primary_from_location_id")
    private UUID primaryFromLocationId;

    @Column(name = "primary_to_location_id")
    private UUID primaryToLocationId;

    @Column(name = "primary_from_location_code")
    private String primaryFromLocationCode;

    @Column(name = "primary_to_location_code")
    private String primaryToLocationCode;

    @NotNull
    @Column(name = "item_count", nullable = false)
    @Builder.Default
    private Integer itemCount = 1;

    @NotNull
    @Column(name = "total_quantity_moved", nullable = false)
    @Builder.Default
    private Integer totalQuantityMoved = 0;

    // Denormalized at write-time so the list view never needs to lazy-load movements
    @Column(name = "product_summary", length = 512)
    private String productSummary;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "auditLog", fetch = FetchType.LAZY)
    @Builder.Default
    private List<StockMovement> movements = new ArrayList<>();
}
