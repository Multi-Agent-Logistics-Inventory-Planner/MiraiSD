package com.mirai.inventoryservice.shipments.api;

import com.mirai.inventoryservice.shipments.api.TrackingLookupRequestDTO;
import com.mirai.inventoryservice.shipments.api.TrackingLookupResponseDTO;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.shipments.application.TrackingService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final TrackingService trackingService;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public TrackingController(TrackingService trackingService,
                               LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.trackingService = trackingService;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    @PostMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<TrackingLookupResponseDTO> lookupTracking(
            @Valid @RequestBody TrackingLookupRequestDTO request) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        TrackingLookupResponseDTO response = trackingService.lookupTracking(context.siteId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{trackingNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<TrackingLookupResponseDTO> getTracking(
            @PathVariable String trackingNumber) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        TrackingLookupResponseDTO response = trackingService.getTracking(context.siteId(), trackingNumber);
        return ResponseEntity.ok(response);
    }
}
