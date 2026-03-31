package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.services.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for storage location management.
 * Storage location types are fixed and seeded automatically.
 * This controller provides read-only endpoints for querying storage locations.
 */
@RestController
@RequestMapping("/api/storage-locations")
public class StorageLocationController {
    private final LocationService locationService;

    public StorageLocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Get all storage locations for the current site.
     */
    @GetMapping
    public ResponseEntity<List<StorageLocation>> getAllStorageLocations() {
        List<StorageLocation> storageLocations = locationService.getAllStorageLocations();
        return ResponseEntity.ok(storageLocations);
    }

    /**
     * Get a storage location by code.
     */
    @GetMapping("/by-code/{code}")
    public ResponseEntity<StorageLocation> getStorageLocationByCode(@PathVariable String code) {
        StorageLocation storageLocation = locationService.getStorageLocationByCode(code);
        return ResponseEntity.ok(storageLocation);
    }

    /**
     * Get storage locations that support inventory (not display-only).
     */
    @GetMapping("/inventory-locations")
    public ResponseEntity<List<StorageLocation>> getInventoryStorageLocations() {
        List<StorageLocation> storageLocations = locationService.getInventoryStorageLocations();
        return ResponseEntity.ok(storageLocations);
    }

    /**
     * Get storage locations that support display tracking.
     */
    @GetMapping("/display-locations")
    public ResponseEntity<List<StorageLocation>> getDisplayStorageLocations() {
        List<StorageLocation> storageLocations = locationService.getDisplayStorageLocations();
        return ResponseEntity.ok(storageLocations);
    }
}
