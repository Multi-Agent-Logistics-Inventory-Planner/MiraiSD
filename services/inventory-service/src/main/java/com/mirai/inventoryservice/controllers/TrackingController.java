package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.TrackingLookupRequestDTO;
import com.mirai.inventoryservice.dtos.responses.TrackingLookupResponseDTO;
import com.mirai.inventoryservice.services.TrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping("/lookup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrackingLookupResponseDTO> lookupTracking(
            @Valid @RequestBody TrackingLookupRequestDTO request) {
        TrackingLookupResponseDTO response = trackingService.lookupTracking(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{trackingNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrackingLookupResponseDTO> getTracking(
            @PathVariable String trackingNumber) {
        TrackingLookupResponseDTO response = trackingService.getTracking(trackingNumber);
        return ResponseEntity.ok(response);
    }
}
