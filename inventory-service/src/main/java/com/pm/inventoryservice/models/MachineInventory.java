package com.pm.inventoryservice.models;

import com.pm.inventoryservice.models.enums.ProductCategory;
import com.pm.inventoryservice.models.enums.ProductSubcategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "machine_inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    private ProductSubcategory subcategory;

    private String description;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateSubcategory() {
        // Subcategory should only be set for BLIND_BOX category
        if (category == ProductCategory.BLIND_BOX && subcategory == null) {
            throw new IllegalStateException("Subcategory is required for Blind Box items");
        }
        if (category != ProductCategory.BLIND_BOX && subcategory != null) {
            throw new IllegalStateException("Subcategory should only be set for Blind Box items");
        }
    }
}
