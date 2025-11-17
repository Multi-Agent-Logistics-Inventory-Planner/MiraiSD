package com.pm.inventoryservice.models;

import com.pm.inventoryservice.models.enums.ProductCategory;
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
@Table(name = "bin_inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bin_id", nullable = false)
    @NotNull
    private Bin bin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory category;

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
    private void validateCategory() {
        // Bins can only contain Plushie or Keychain items
        if (category != ProductCategory.PLUSHIE && category != ProductCategory.KEYCHAIN) {
            throw new IllegalStateException("Bins can only contain Plushie or Keychain items");
        }
    }
}