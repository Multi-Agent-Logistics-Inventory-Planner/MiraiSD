package com.mirai.inventoryservice.models.storage;

import com.mirai.inventoryservice.models.Site;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
 * Represents a category of storage locations (e.g., "Box Bins", "Gachapons").
 * Replaces the 11 separate location type tables with a single unified table.
 *
 * Behavior matrix based on flags:
 * - has_display=false, is_display_only=false: Storage only (Box Bins, Racks, Not Assigned)
 * - has_display=true, is_display_only=false: Storage + Display (Claw Machines, Pushers)
 * - has_display=true, is_display_only=true: Display only (Gachapons, Keychains)
 */
@Entity
@Table(name = "storage_locations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"site_id", "code"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    @NotNull
    private Site site;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "code_prefix", length = 10)
    private String codePrefix;

    @Column(length = 50)
    private String icon;

    @Column(name = "has_display", nullable = false)
    @Builder.Default
    private Boolean hasDisplay = false;

    @Column(name = "is_display_only", nullable = false)
    @Builder.Default
    private Boolean isDisplayOnly = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
