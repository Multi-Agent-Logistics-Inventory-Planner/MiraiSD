package com.mirai.inventoryservice.displays.domain;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
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
                @Index(name = "idx_machine_display_started_at", columnList = "started_at"),
                @Index(name = "idx_machine_display_location", columnList = "location_id")
        })
@NamedEntityGraph(name = "MachineDisplay.withProduct", attributeNodes = @NamedAttributeNode("product"))
@NamedEntityGraph(name = "MachineDisplay.withLocation", attributeNodes = @NamedAttributeNode("location"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineDisplay {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // New unified location reference (preferred)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @NotNull
    private Location location;

    /**
     * The site this display belongs to. Nullable at the JPA level because the underlying column
     * (V72) is expand-phase nullable pending a separate constraining migration (.specs/
     * phase-7-displays T-5) once every writer is confirmed to set it; every production write path
     * sets it explicitly, derived from the display's location rather than the caller's authorized
     * site context, since a display always belongs to the site that owns its machine's location.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    // Legacy fields - kept for backward compatibility during migration
    // TODO: Remove in Phase C after 30+ days
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
