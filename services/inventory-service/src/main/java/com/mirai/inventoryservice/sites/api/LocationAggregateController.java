package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.sites.application.LocationAggregateService;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
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
 *
 * <p>Owned by {@code sites} per .specs/phase-6-inventory/log.md R-1 (2026-09-09): its repository's
 * native query reads {@code locations}/{@code storage_locations} (sites), {@code location_inventory}
 * (inventory) and {@code machine_display} (displays) in one statement -- an approved cross-module
 * read projection under docs/specs/spring-domain-modular-monolith.md §7.4, not decomposed or moved
 * into any of the three owning modules individually.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationAggregateController {

    private final LocationAggregateService locationAggregateService;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public LocationAggregateController(LocationAggregateService locationAggregateService,
                                       LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.locationAggregateService = locationAggregateService;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    /**
     * Get all locations with their inventory counts in a single request.
     * Replaces the N+1 pattern of fetching locations then counts individually.
     *
     * @param type Optional filter by location type (BOX_BIN, RACK, CABINET, etc.)
     * @return List of locations with inventory record counts and total quantities
     */
    @GetMapping("/with-counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LocationWithCountsDTO>> getLocationsWithCounts(
            @RequestParam(required = false) LocationType type) {

        List<LocationWithCountsDTO> locations;
        var main = legacyMainSiteContextResolver.requireMain();
        if (type != null) {
            locations = locationAggregateService.getLocationsByTypeWithCounts(type, main.siteId());
        } else {
            locations = locationAggregateService.getAllLocationsWithCounts(main.siteId());
        }

        return ResponseEntity.ok(locations);
    }
}
