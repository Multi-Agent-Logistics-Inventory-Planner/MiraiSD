package com.mirai.inventoryservice.models.kuji;

import com.mirai.inventoryservice.models.Product;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kuji_box_tiers",
        indexes = {
                @Index(name = "idx_kuji_box_tiers_box", columnList = "box_id"),
                @Index(name = "idx_kuji_box_tiers_linked_product", columnList = "linked_product_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KujiBoxTier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_id", nullable = false)
    @ToString.Exclude
    private KujiBox box;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 50)
    private String letter;

    /** Optional link to a stocked product. When set, draws decrement this product's LocationInventory at the box's location. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_product_id")
    @ToString.Exclude
    private Product linkedProduct;

    /** Direct access to FK column for fast lookups without lazy loading. */
    @Column(name = "linked_product_id", insertable = false, updatable = false)
    private UUID linkedProductId;

    @NotNull
    @Column(nullable = false)
    private Integer count;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * True when the linked product was auto-created at open-box (one-shot prize for
     * this box only). At close-box such products are soft-deleted and their images
     * are hard-deleted from storage.
     */
    @NotNull
    @Column(name = "auto_created_product", nullable = false)
    @Builder.Default
    private Boolean autoCreatedProduct = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
