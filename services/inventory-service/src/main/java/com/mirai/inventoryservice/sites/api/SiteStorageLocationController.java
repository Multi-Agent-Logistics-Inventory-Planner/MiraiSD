package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Site-scoped, read-only storage location queries - the {@code StorageLocationController}
 * counterpart of {@link SiteLocationController}. Storage location types are fixed and seeded
 * automatically; this stays read-only like the legacy controller. Reads the site off
 * {@code AuthorizedSiteContextHolder}, matching {@code SitePermissionsController}/
 * {@code SiteLocationController}. Every handler still declares the {@code siteId} path variable
 * (even though it goes unused in the body) so springdoc/the generated OpenAPI contract exposes it
 * as a required parameter for the typed API client. Handler names are distinct from
 * {@link StorageLocationController}'s so springdoc doesn't collide their operation IDs and
 * silently reassign the legacy ones. The legacy {@code /api/storage-locations} routes are
 * untouched.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/storage-locations")
public class SiteStorageLocationController {
    private final LocationService locationService;

    public SiteStorageLocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public ResponseEntity<List<StorageLocation>> getSiteStorageLocations(@PathVariable UUID siteId) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(locationService.getAllStorageLocations(contextSiteId));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<StorageLocation> getSiteStorageLocationByCode(
            @PathVariable UUID siteId, @PathVariable String code) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(locationService.getStorageLocationByCode(contextSiteId, code));
    }

    @GetMapping("/inventory-locations")
    public ResponseEntity<List<StorageLocation>> getSiteInventoryStorageLocations(@PathVariable UUID siteId) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(locationService.getInventoryStorageLocations(contextSiteId));
    }

    @GetMapping("/display-locations")
    public ResponseEntity<List<StorageLocation>> getSiteDisplayStorageLocations(@PathVariable UUID siteId) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(locationService.getDisplayStorageLocations(contextSiteId));
    }
}
