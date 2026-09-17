package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for storage location management.
 * Storage location types are fixed and seeded automatically.
 * This controller provides read-only endpoints for querying storage locations.
 */
@RestController
@RequestMapping("/api/storage-locations")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class StorageLocationController {
    private final LocationService locationService;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public StorageLocationController(LocationService locationService,
                                     LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.locationService = locationService;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    /**
     * Get all storage locations for the current site.
     */
    @GetMapping
    public ResponseEntity<List<StorageLocation>> getAllStorageLocations() {
        List<StorageLocation> storageLocations = locationService.getAllStorageLocations(
                legacyMainSiteContextResolver.requireMain().siteId());
        return ResponseEntity.ok(storageLocations);
    }

    /**
     * Get a storage location by code.
     */
    @GetMapping("/by-code/{code}")
    public ResponseEntity<StorageLocation> getStorageLocationByCode(@PathVariable String code) {
        StorageLocation storageLocation = locationService.getStorageLocationByCode(
                legacyMainSiteContextResolver.requireMain().siteId(), code);
        return ResponseEntity.ok(storageLocation);
    }

    /**
     * Get storage locations that support inventory (not display-only).
     */
    @GetMapping("/inventory-locations")
    public ResponseEntity<List<StorageLocation>> getInventoryStorageLocations() {
        List<StorageLocation> storageLocations = locationService.getInventoryStorageLocations(
                legacyMainSiteContextResolver.requireMain().siteId());
        return ResponseEntity.ok(storageLocations);
    }

    /**
     * Get storage locations that support display tracking.
     */
    @GetMapping("/display-locations")
    public ResponseEntity<List<StorageLocation>> getDisplayStorageLocations() {
        List<StorageLocation> storageLocations = locationService.getDisplayStorageLocations(
                legacyMainSiteContextResolver.requireMain().siteId());
        return ResponseEntity.ok(storageLocations);
    }
}
