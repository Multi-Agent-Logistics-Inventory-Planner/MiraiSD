package com.mirai.inventoryservice.kuji.domain;

import com.mirai.inventoryservice.displays.domain.MachineDisplay;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.models.enums.KujiBoxStatus;
import com.mirai.inventoryservice.sites.domain.Location;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kuji_boxes",
        indexes = {
                @Index(name = "idx_kuji_boxes_product", columnList = "product_id, status"),
                @Index(name = "idx_kuji_boxes_location", columnList = "location_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KujiBox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @ToString.Exclude
    private Location location;

    /** Optional link to a machine_display row when the box is on display. May go stale if display ends. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_display_id")
    @ToString.Exclude
    private MachineDisplay machineDisplay;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private KujiBoxStatus status = KujiBoxStatus.OPEN;

    /**
     * Raw UUID, not a JPA relation, to avoid a new kuji -> sites module edge
     * (.specs/phase-7-kuji-lootbox T-2). Nullable until V78 constrains it; every new box must
     * resolve this from the authorized site context, never a default, and it must always match
     * {@link #location}'s site (enforced in the database by the V78 consistency trigger).
     */
    @Column(name = "site_id")
    private UUID siteId;

    @Column(length = 120)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "box", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    @ToString.Exclude
    private List<KujiBoxTier> tiers = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        if (openedAt == null) {
            openedAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = KujiBoxStatus.OPEN;
        }
    }

    public boolean isOpen() {
        return status == KujiBoxStatus.OPEN;
    }
}
