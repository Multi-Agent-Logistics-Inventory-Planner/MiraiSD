package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.services.LocationService;
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
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Get all locations, optionally filtered by storage location code.
     */
    @GetMapping
    public ResponseEntity<List<Location>> getAllLocations(
            @RequestParam(required = false) String storageLocation) {
        List<Location> locations;
        if (storageLocation != null && !storageLocation.isBlank()) {
            locations = locationService.getLocationsByStorageLocationCode(storageLocation);
        } else {
            locations = locationService.getAllLocations();
        }
        return ResponseEntity.ok(locations);
    }

    /**
     * Get a location by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable UUID id) {
        Location location = locationService.getLocationById(id);
        return ResponseEntity.ok(location);
    }

    /**
     * Create a new location.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Location> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        Location location = locationService.createLocation(
                request.getStorageLocationId(),
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
        Location location = locationService.updateLocation(id, request.getLocationCode());
        return ResponseEntity.ok(location);
    }

    /**
     * Delete a location.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
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
