package com.pm.inventoryservice.models;

import com.pm.inventoryservice.models.enums.LocationType;
import com.pm.inventoryservice.models.enums.StockMovementReason;
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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @NotNull
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "from_box_id")
    private UUID fromBoxId;

    @Column(name = "to_box_id")
    private UUID toBoxId;

    @NotNull
    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementReason reason;

    @Column(name = "actor_id")
    private UUID actorId;  // User who performed the action

    @NotNull
    @Column(nullable = false)
    private OffsetDateTime at;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    private void prePersist() {
        if (at == null) {
            at = OffsetDateTime.now();
        }
    }
}
