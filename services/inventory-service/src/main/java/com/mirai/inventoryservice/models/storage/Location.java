package com.mirai.inventoryservice.models.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an individual location unit within a storage location category.
 * Examples: B1, B2 (Box Bins), G1, G2 (Gachapons), S1, S2 (Single Claw Machines)
 *
 * Replaces the individual location tables (box_bins, racks, gachapons, etc.)
 * with a single unified table.
 */
@Entity
@Table(name = "locations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"storage_location_id", "location_code"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_location_id", nullable = false)
    @NotNull
    private StorageLocation storageLocation;

    @NotBlank
    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Get the full location code including the storage location type.
     * Example: "BOX_BINS:B1" or "GACHAPON:G1"
     */
    public String getFullLocationCode() {
        if (storageLocation != null) {
            return storageLocation.getCode() + ":" + locationCode;
        }
        return locationCode;
    }
}
