package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.LocationInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.LocationInventoryResponseDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.application.LocationInventoryService;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Unified controller for inventory operations at any location.
 * Replaces the 9 type-specific controllers (BoxBinInventoryController, etc.)
 */
@RestController
public class LocationInventoryController {
    private final LocationInventoryService locationInventoryService;
    private final LocationInventoryMapper locationInventoryMapper;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public LocationInventoryController(
            LocationInventoryService locationInventoryService,
            LocationInventoryMapper locationInventoryMapper,
            LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.locationInventoryService = locationInventoryService;
        this.locationInventoryMapper = locationInventoryMapper;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    // ========= Location-level endpoints =========

    @GetMapping("/api/locations/{locationId}/inventory")
    public ResponseEntity<List<LocationInventoryResponseDTO>> listInventoryAtLocation(
            @PathVariable UUID locationId) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        List<LocationInventory> inventories = locationInventoryService.listInventoryAtLocation(context.siteId(), locationId);
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/api/locations/{locationId}/inventory/{inventoryId}")
    public ResponseEntity<LocationInventoryResponseDTO> getInventoryById(
            @PathVariable UUID locationId,
            @PathVariable UUID inventoryId) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        LocationInventory inventory = locationInventoryService.getInventoryById(context.siteId(), inventoryId);
        if (!inventory.getLocation().getId().equals(locationId)) {
            throw new com.mirai.inventoryservice.inventory.domain.InventoryNotFoundException(
                    "Inventory not found with id: " + inventoryId);
        }
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping("/api/locations/{locationId}/inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LocationInventoryResponseDTO> addInventory(
            @PathVariable UUID locationId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        LocationInventory inventory = locationInventoryService.addInventory(
                context.siteId(),
                context.backendUserId(),
                locationId,
                requestDTO.getItemId(),
                requestDTO.getQuantity(),
                requestDTO.getReason(),
                requestDTO.getIntakeUnit(),
                requestDTO.getIntakeQty());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locationInventoryMapper.toResponseDTO(inventory));
    }

    // The untracked PUT (silent absolute-quantity set, no StockMovement/audit/outbox) stays
    // removed even through the 6e R-3 revert of this controller's other routes -- it was always
    // a standing AC-4 violation (6d's R-9 resolution note), not compatibility debt requiring the
    // release/stabilization gate the GET/POST/DELETE routes below do. All quantity edits go
    // through the audited adjustment endpoint (v1 or the legacy /api/stock-movements/batch-adjust).

    @DeleteMapping("/api/locations/{locationId}/inventory/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID locationId,
            @PathVariable UUID inventoryId,
            @RequestParam(required = false) UUID actorId) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        // actorId remains accepted for URL compatibility but is never trusted for audit identity.
        locationInventoryService.deleteInventory(
                context.siteId(), context.backendUserId(), locationId, inventoryId, null);
        return ResponseEntity.noContent().build();
    }

    // ========= Storage location-level endpoints =========

    @GetMapping("/api/storage-locations/{storageLocationId}/inventory")
    public ResponseEntity<List<LocationInventoryResponseDTO>> listInventoryByStorageLocation(
            @PathVariable UUID storageLocationId) {
        AuthorizedSiteContext context = legacyMainSiteContextResolver.requireMain();
        List<LocationInventory> inventories = locationInventoryService
                .listInventoryByStorageLocation(context.siteId(), storageLocationId);
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTOList(inventories));
    }

}
