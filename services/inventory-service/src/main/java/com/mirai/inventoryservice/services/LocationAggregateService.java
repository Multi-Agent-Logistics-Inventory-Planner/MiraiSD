package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.LocationWithCountsDTO;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.repositories.LocationAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
     */
    public List<LocationWithCountsDTO> getAllLocationsWithCounts() {
        return locationAggregateRepository.findAllLocationsWithCounts();
    }

    /**
     * Get locations of a specific type with their inventory counts.
     *
     * @param locationType The type of location to filter by (e.g., BOX_BIN, RACK)
     */
    public List<LocationWithCountsDTO> getLocationsByTypeWithCounts(LocationType locationType) {
        if (locationType == LocationType.NOT_ASSIGNED) {
            return List.of();
        }
        String storageLocationCode = mapLocationTypeToStorageCode(locationType);
        return locationAggregateRepository.findLocationsByTypeWithCounts(storageLocationCode);
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
