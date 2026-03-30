package com.mirai.inventoryservice.models.inventory;

import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.storage.Location;
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

/**
 * Unified inventory table that replaces the 9 separate inventory tables.
 * Tracks product quantities at specific locations.
 *
 * Note: location_id is NOT NULL - all inventory has a location.
 * NOT_ASSIGNED is an explicit storage location category with its own location unit.
 *
 * Replaces:
 * - box_bin_inventory
 * - rack_inventory
 * - cabinet_inventory
 * - window_inventory
 * - single_claw_machine_inventory
 * - double_claw_machine_inventory
 * - four_corner_machine_inventory
 * - pusher_machine_inventory
 * - not_assigned_inventory
 */
@Entity
@Table(name = "location_inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"location_id", "product_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @NotNull
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    @NotNull
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
