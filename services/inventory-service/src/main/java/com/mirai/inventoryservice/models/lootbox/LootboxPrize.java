package com.mirai.inventoryservice.models.lootbox;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lootbox_prizes",
       indexes = {
           @Index(name = "idx_lootbox_prizes_tier", columnList = "tier_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LootboxPrize {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    // Flyway owns the FK (V46: ON DELETE CASCADE). NO_CONSTRAINT stops Hibernate's
    // ddl-auto=update from recreating the constraint with default NO_ACTION semantics
    // on dev restart, which silently breaks hard-delete of tiers.
    @JoinColumn(
            name = "tier_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    private LootboxTier tier;

    @Column(name = "tier_id", insertable = false, updatable = false)
    private UUID tierId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
