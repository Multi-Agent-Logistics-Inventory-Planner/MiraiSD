package com.mirai.inventoryservice.sites.api;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.sites.application.LocationAggregateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Site-scoped counterpart to {@link LocationAggregateController} (.specs/phase-6-inventory 6e,
 * T-6e-be-2/3). Kept as its own controller class, mirroring {@link LocationAggregateController}'s
 * own R-1 note: this is an approved cross-module read projection under
 * docs/specs/spring-domain-modular-monolith.md §7.4, not decomposed into the three owning
 * modules. A separate class also avoids a springdoc operationId collision with the legacy
 * controller, the same trap {@link SiteLocationController} already documents.
 *
 * <p>Reads the site off {@link AuthorizedSiteContextHolder}, never the raw {@code siteId} path
 * variable, matching every other v1 site-scoped controller. The {@code /with-counts} literal
 * segment takes precedence over {@link SiteLocationController#getSiteLocationById}'s
 * {@code /{id}} pattern (Spring's literal-beats-variable path matching), pinned by
 * {@code SiteLocationAggregateControllerSecurityIT}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/locations")
public class SiteLocationAggregateController {

    private final LocationAggregateService locationAggregateService;

    public SiteLocationAggregateController(LocationAggregateService locationAggregateService) {
        this.locationAggregateService = locationAggregateService;
    }

    @GetMapping("/with-counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LocationWithCountsDTO>> getSiteLocationsWithCounts(
            @PathVariable UUID siteId,
            @RequestParam(required = false) LocationType type) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();

        List<LocationWithCountsDTO> locations;
        if (type != null) {
            locations = locationAggregateService.getLocationsByTypeWithCounts(type, contextSiteId);
        } else {
            locations = locationAggregateService.getAllLocationsWithCounts(contextSiteId);
        }

        return ResponseEntity.ok(locations);
    }
}
