package com.mirai.inventoryservice.models;

import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "machine_display",
        indexes = {
                @Index(name = "idx_machine_display_active", columnList = "location_type, machine_id, ended_at"),
                @Index(name = "idx_machine_display_product", columnList = "product_id"),
                @Index(name = "idx_machine_display_started_at", columnList = "started_at")
        })
@NamedEntityGraph(name = "MachineDisplay.withProduct", attributeNodes = @NamedAttributeNode("product"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineDisplay {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @NotNull
    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @PrePersist
    private void prePersist() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    /**
     * Check if this display is currently active (not ended)
     */
    public boolean isActive() {
        return endedAt == null;
    }
}
