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
        return locationAggregateRepository.findLocationsByTypeWithCounts(locationType.name());
    }
}
