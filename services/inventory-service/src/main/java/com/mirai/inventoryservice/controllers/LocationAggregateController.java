package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.LocationWithCountsDTO;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.services.LocationAggregateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for aggregated location endpoints.
 * Provides optimized batch endpoints to reduce N+1 API calls from the frontend.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationAggregateController {

    private final LocationAggregateService locationAggregateService;

    public LocationAggregateController(LocationAggregateService locationAggregateService) {
        this.locationAggregateService = locationAggregateService;
    }

    /**
     * Get all locations with their inventory counts in a single request.
     * Replaces the N+1 pattern of fetching locations then counts individually.
     *
     * @param type Optional filter by location type (BOX_BIN, RACK, CABINET, etc.)
     * @return List of locations with inventory record counts and total quantities
     */
    @GetMapping("/with-counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<LocationWithCountsDTO>> getLocationsWithCounts(
            @RequestParam(required = false) LocationType type) {

        List<LocationWithCountsDTO> locations;
        if (type != null) {
            locations = locationAggregateService.getLocationsByTypeWithCounts(type);
        } else {
            locations = locationAggregateService.getAllLocationsWithCounts();
        }

        return ResponseEntity.ok(locations);
    }
}
