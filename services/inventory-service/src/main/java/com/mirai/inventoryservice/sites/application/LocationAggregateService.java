package com.mirai.inventoryservice.sites.application;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.api.LocationWithCountsDTO;
import com.mirai.inventoryservice.sites.infrastructure.LocationAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for fetching aggregated location data with inventory counts.
 * Provides optimized endpoints to reduce N+1 query problems.
 */
@Service
@Transactional(readOnly = true)
public class LocationAggregateService {

    private final LocationAggregateRepository locationAggregateRepository;

    public LocationAggregateService(LocationAggregateRepository locationAggregateRepository) {
        this.locationAggregateRepository = locationAggregateRepository;
    }

    /**
     * Get all locations across all types with their inventory counts.
     *
     * @deprecated site-blind; use {@link #getAllLocationsWithCounts(UUID)}.
     */
    @Deprecated
    public List<LocationWithCountsDTO> getAllLocationsWithCounts() {
        return locationAggregateRepository.findAllLocationsWithCounts();
    }

    /**
     * Get locations of a specific type with their inventory counts.
     *
     * @param locationType The type of location to filter by (e.g., BOX_BIN, RACK)
     * @deprecated site-blind; use {@link #getLocationsByTypeWithCounts(LocationType, UUID)}.
     */
    @Deprecated
    public List<LocationWithCountsDTO> getLocationsByTypeWithCounts(LocationType locationType) {
        if (locationType == LocationType.NOT_ASSIGNED) {
            return List.of();
        }
        String storageLocationCode = mapLocationTypeToStorageCode(locationType);
        return locationAggregateRepository.findLocationsByTypeWithCounts(storageLocationCode);
    }

    /**
     * Site-scoped: get all locations across all types with their inventory counts, for
     * {@code siteId} only (.specs/phase-6-inventory 6e, T-6e-be-2/3).
     */
    public List<LocationWithCountsDTO> getAllLocationsWithCounts(UUID siteId) {
        return locationAggregateRepository.findAllLocationsWithCounts(siteId);
    }

    /**
     * Site-scoped: get locations of a specific type with their inventory counts, for
     * {@code siteId} only (.specs/phase-6-inventory 6e, T-6e-be-2/3).
     *
     * @param locationType The type of location to filter by (e.g., BOX_BIN, RACK)
     */
    public List<LocationWithCountsDTO> getLocationsByTypeWithCounts(LocationType locationType, UUID siteId) {
        if (locationType == LocationType.NOT_ASSIGNED) {
            return List.of();
        }
        String storageLocationCode = mapLocationTypeToStorageCode(locationType);
        return locationAggregateRepository.findLocationsByTypeWithCounts(storageLocationCode, siteId);
    }

    /**
     * Maps LocationType enum values to storage_locations.code values.
     * The storage location codes use slightly different naming conventions.
     */
    private String mapLocationTypeToStorageCode(LocationType locationType) {
        return switch (locationType) {
            case BOX_BIN -> "BOX_BINS";
            case RACK -> "RACKS";
            case CABINET -> "CABINETS";
            case SHELF -> "SHELVES";
            case WINDOW -> "WINDOWS";
            case SINGLE_CLAW_MACHINE -> "SINGLE_CLAW";
            case DOUBLE_CLAW_MACHINE -> "DOUBLE_CLAW";
            case FOUR_CORNER_MACHINE -> "FOUR_CORNER";
            case PUSHER_MACHINE -> "PUSHER";
            case GACHAPON -> "GACHAPON";
            case KEYCHAIN_MACHINE -> "KEYCHAIN";
            case NOT_ASSIGNED -> "NOT_ASSIGNED";
        };
    }
}
