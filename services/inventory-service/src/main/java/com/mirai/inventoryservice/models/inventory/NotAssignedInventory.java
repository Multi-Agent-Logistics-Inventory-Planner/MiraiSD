package com.mirai.inventoryservice.models.inventory;

import com.mirai.inventoryservice.models.Product;
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
@Table(name = "not_assigned_inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotAssignedInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // NOTE: No location reference - NOT_ASSIGNED items are not at any physical location

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @NotNull
    private Product item;

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
}
