package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for {@link SiteLocationController} (.specs/phase-6-inventory 6e, T-6e-be-7): closes a
 * standing AC-5 gap ("v1 routes expose DTOs through the application boundary") where the
 * controller previously returned the raw {@link Location} JPA entity. Flattens
 * {@code storageLocation} to its id/code (matching the shape the web client already extracts
 * by hand) while also nesting a bounded {@link StorageLocationSummary} - not the raw
 * {@link StorageLocation} entity, which would re-leak its own {@code site} association - so the
 * response keeps the "storageLocation is always present" contract the previous raw-entity
 * response made.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteLocationDTO {
    private UUID id;

    @NotBlank
    private String locationCode;

    private String fullLocationCode;

    private UUID storageLocationId;
    private String storageLocationCode;

    @NotNull
    private StorageLocationSummary storageLocation;

    private Map<String, Object> metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static SiteLocationDTO from(Location location) {
        StorageLocation storageLocation = location.getStorageLocation();
        return SiteLocationDTO.builder()
                .id(location.getId())
                .locationCode(location.getLocationCode())
                .fullLocationCode(location.getFullLocationCode())
                .storageLocationId(storageLocation != null ? storageLocation.getId() : null)
                .storageLocationCode(storageLocation != null ? storageLocation.getCode() : null)
                .storageLocation(StorageLocationSummary.from(storageLocation))
                .metadata(location.getMetadata())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageLocationSummary {
        @NotNull
        private UUID id;

        @NotBlank
        private String code;

        @NotBlank
        private String name;

        @NotNull
        private SiteSummary site;

        static StorageLocationSummary from(StorageLocation storageLocation) {
            if (storageLocation == null) {
                return null;
            }
            return StorageLocationSummary.builder()
                    .id(storageLocation.getId())
                    .code(storageLocation.getCode())
                    .name(storageLocation.getName())
                    .site(SiteSummary.from(storageLocation.getSite()))
                    .build();
        }
    }

    /**
     * Bounded, non-entity summary of {@link Site} - id/code/name only, deliberately excluding
     * address fields the raw entity carried. Every {@link com.mirai.inventoryservice.sites.infrastructure.LocationRepository}
     * query already {@code JOIN FETCH}es {@code storageLocation.site}, so nesting this costs no
     * extra query.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiteSummary {
        @NotNull
        private UUID id;

        @NotBlank
        private String code;

        @NotBlank
        private String name;

        static SiteSummary from(Site site) {
            if (site == null) {
                return null;
            }
            return SiteSummary.builder()
                    .id(site.getId())
                    .code(site.getCode())
                    .name(site.getName())
                    .build();
        }
    }
}
