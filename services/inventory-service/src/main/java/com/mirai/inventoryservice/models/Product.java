package com.mirai.inventoryservice.models;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Supplier import for preferred supplier relationship

@Entity
@Table(name = "products")
@org.hibernate.annotations.BatchSize(size = 50)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String sku;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @ToString.Exclude
    private Product parent;

    // Direct access to FK column to avoid lazy loading when only ID is needed
    @Column(name = "parent_id", insertable = false, updatable = false)
    private UUID parentId;

    /** Prize letter or label for Kuji children (e.g., A, B, C, Last Prize). Null for non-prize products. */
    @Column(length = 50)
    private String letter;

    /** Quantity per kuji set for prize products. Used to auto-calculate ordered/received quantities. */
    @Column(name = "template_quantity")
    private Integer templateQuantity;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    @ToString.Exclude
    private List<Product> children = new ArrayList<>();

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reorder_point")
    @Builder.Default
    private Integer reorderPoint = 10;

    @Column(name = "target_stock_level")
    @Builder.Default
    private Integer targetStockLevel = 50;

    @Column(name = "lead_time_days")
    @Builder.Default
    private Integer leadTimeDays = 14;

    @Column(name = "unit_cost", precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "quantity")
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Preferred supplier for this product. Used for lead time calculations.
     * Auto-assigned when a shipment containing this product is delivered.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    @ToString.Exclude
    private Supplier preferredSupplier;

    /**
     * Direct access to FK column to avoid lazy loading when only ID is needed.
     */
    @Column(name = "preferred_supplier_id", insertable = false, updatable = false)
    private UUID preferredSupplierId;

    /**
     * True if preferredSupplier was auto-assigned from a shipment delivery.
     * False if manually set by an admin.
     */
    @Column(name = "preferred_supplier_auto")
    @Builder.Default
    private Boolean preferredSupplierAuto = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Returns true if this is a root/standalone product (no parent)
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * Returns true if this product has children (is a parent/Kuji product)
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}
