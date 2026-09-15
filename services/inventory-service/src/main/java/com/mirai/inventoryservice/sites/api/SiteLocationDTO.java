package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.sites.domain.Location;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for {@link SiteLocationController} (.specs/phase-6-inventory 6e, T-6e-be-7): closes a
 * standing AC-5 gap ("v1 routes expose DTOs through the application boundary") where the
 * controller previously returned the raw {@link Location} JPA entity. Flattens
 * {@code storageLocation} to its id/code, matching the shape the web client already extracts
 * by hand from the entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteLocationDTO {
    private UUID id;
    private String locationCode;
    private UUID storageLocationId;
    private String storageLocationCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static SiteLocationDTO from(Location location) {
        return SiteLocationDTO.builder()
                .id(location.getId())
                .locationCode(location.getLocationCode())
                .storageLocationId(location.getStorageLocation() != null ? location.getStorageLocation().getId() : null)
                .storageLocationCode(location.getStorageLocation() != null ? location.getStorageLocation().getCode() : null)
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
