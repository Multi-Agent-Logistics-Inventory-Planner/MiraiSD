package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.shipments.application.TrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Site-scoped tracking routes (.specs/phase-7-shipments T-5, AC-5), mirroring
 * {@link TrackingController}'s legacy routes route-for-route under
 * {@code /api/v1/sites/{siteId}/tracking}, same pattern as {@link SiteShipmentController}.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/tracking")
public class SiteTrackingController {

    private final TrackingService trackingService;

    public SiteTrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<TrackingLookupResponseDTO> lookupSiteTracking(
            @PathVariable UUID siteId,
            @Valid @RequestBody TrackingLookupRequestDTO request) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        TrackingLookupResponseDTO response = trackingService.lookupTracking(contextSiteId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{trackingNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<TrackingLookupResponseDTO> getSiteTracking(
            @PathVariable UUID siteId,
            @PathVariable String trackingNumber) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        TrackingLookupResponseDTO response = trackingService.getTracking(contextSiteId, trackingNumber);
        return ResponseEntity.ok(response);
    }
}
