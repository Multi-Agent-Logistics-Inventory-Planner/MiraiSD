package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.domain.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Site-scoped location CRUD - the reference vertical slice for docs/plans/enterprise-modernization.md
 * Phase 4. Reads the site off {@link AuthorizedSiteContextHolder} (already resolved by
 * {@code identity.infrastructure.SiteAccessAuthorizationFilter} for this path) rather than trusting
 * the raw {@code siteId} path variable directly, matching {@code SitePermissionsController}. Every
 * handler still declares the {@code siteId} path variable (even though it goes unused in the body)
 * so springdoc/the generated OpenAPI contract exposes it as a required parameter - without it, the
 * typed API client has no way to supply the URL segment. Handler and DTO names are distinct from
 * {@link LocationController}'s (e.g. {@code getSiteLocations}, not {@code getAllLocations}) so
 * springdoc doesn't collide their operation IDs and silently reassign the legacy ones. The legacy,
 * unscoped {@code /api/locations} routes on {@link LocationController} are untouched.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/locations")
public class SiteLocationController {
    private final LocationService locationService;

    public SiteLocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public ResponseEntity<List<SiteLocationDTO>> getSiteLocations(
            @PathVariable UUID siteId,
            @RequestParam(required = false) String storageLocation) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        List<Location> locations;
        if (storageLocation != null && !storageLocation.isBlank()) {
            locations = locationService.getLocationsByStorageLocationCode(contextSiteId, storageLocation);
        } else {
            locations = locationService.getAllLocations(contextSiteId);
        }
        return ResponseEntity.ok(locations.stream().map(SiteLocationDTO::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiteLocationDTO> getSiteLocationById(@PathVariable UUID siteId, @PathVariable UUID id) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(SiteLocationDTO.from(locationService.getLocationById(contextSiteId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<SiteLocationDTO> createSiteLocation(
            @PathVariable UUID siteId,
            @Valid @RequestBody CreateSiteLocationRequest request) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Location location = locationService.createLocation(
                contextSiteId, request.getStorageLocationId(), request.getLocationCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(SiteLocationDTO.from(location));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<SiteLocationDTO> updateSiteLocation(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSiteLocationRequest request) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Location location = locationService.updateLocation(contextSiteId, id, request.getLocationCode());
        return ResponseEntity.ok(SiteLocationDTO.from(location));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteSiteLocation(@PathVariable UUID siteId, @PathVariable UUID id) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        locationService.deleteLocation(contextSiteId, id);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class CreateSiteLocationRequest {
        @NotBlank(message = "Location code is required")
        private String locationCode;

        @NotNull(message = "Storage location ID is required")
        private UUID storageLocationId;
    }

    @Data
    public static class UpdateSiteLocationRequest {
        @NotBlank(message = "Location code is required")
        private String locationCode;
    }
}
