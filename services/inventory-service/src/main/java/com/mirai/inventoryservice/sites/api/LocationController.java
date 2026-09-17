package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for location management.
 * Provides CRUD endpoints for individual location units within storage locations.
 */
@RestController
@RequestMapping("/api/locations")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class LocationController {
    private final LocationService locationService;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public LocationController(LocationService locationService,
                              LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.locationService = locationService;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    /**
     * Get all locations, optionally filtered by storage location code.
     */
    @GetMapping
    public ResponseEntity<List<Location>> getAllLocations(
            @RequestParam(required = false) String storageLocation) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        List<Location> locations;
        if (storageLocation != null && !storageLocation.isBlank()) {
            locations = locationService.getLocationsByStorageLocationCode(context.siteId(), storageLocation);
        } else {
            locations = locationService.getAllLocations(context.siteId());
        }
        return ResponseEntity.ok(locations);
    }

    /**
     * Get a location by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable UUID id) {
        Location location = locationService.getLocationById(legacyMainSiteContextResolver.requireMain().siteId(), id);
        return ResponseEntity.ok(location);
    }

    /**
     * Create a new location.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Location> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        Location location = locationService.createLocation(
                context.siteId(), request.getStorageLocationId(),
                request.getLocationCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(location);
    }

    /**
     * Update a location.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Location> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request) {
        Location location = locationService.updateLocation(
                legacyMainSiteContextResolver.requireMain().siteId(), id, request.getLocationCode());
        return ResponseEntity.ok(location);
    }

    /**
     * Delete a location.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(legacyMainSiteContextResolver.requireMain().siteId(), id);
        return ResponseEntity.noContent().build();
    }

    // Request DTOs

    @Data
    public static class CreateLocationRequest {
        @NotBlank(message = "Location code is required")
        private String locationCode;

        @jakarta.validation.constraints.NotNull(message = "Storage location ID is required")
        private UUID storageLocationId;
    }

    @Data
    public static class UpdateLocationRequest {
        @NotBlank(message = "Location code is required")
        private String locationCode;
    }
}
