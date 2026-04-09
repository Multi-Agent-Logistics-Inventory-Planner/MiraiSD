package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.LocationInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.LocationInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.services.LocationInventoryService;
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

    public LocationInventoryController(
            LocationInventoryService locationInventoryService,
            LocationInventoryMapper locationInventoryMapper) {
        this.locationInventoryService = locationInventoryService;
        this.locationInventoryMapper = locationInventoryMapper;
    }

    // ========= Location-level endpoints =========

    @GetMapping("/api/locations/{locationId}/inventory")
    public ResponseEntity<List<LocationInventoryResponseDTO>> listInventoryAtLocation(
            @PathVariable UUID locationId) {
        List<LocationInventory> inventories = locationInventoryService.listInventoryAtLocation(locationId);
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/api/locations/{locationId}/inventory/{inventoryId}")
    public ResponseEntity<LocationInventoryResponseDTO> getInventoryById(
            @PathVariable UUID locationId,
            @PathVariable UUID inventoryId) {
        LocationInventory inventory = locationInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping("/api/locations/{locationId}/inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LocationInventoryResponseDTO> addInventory(
            @PathVariable UUID locationId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        LocationInventory inventory = locationInventoryService.addInventory(
                locationId,
                requestDTO.getItemId(),
                requestDTO.getQuantity(),
                requestDTO.getActorId(),
                requestDTO.getReason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locationInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/api/locations/{locationId}/inventory/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LocationInventoryResponseDTO> updateInventory(
            @PathVariable UUID locationId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        LocationInventory inventory = locationInventoryService.updateInventoryQuantity(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/api/locations/{locationId}/inventory/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID locationId,
            @PathVariable UUID inventoryId,
            @RequestParam(required = false) UUID actorId) {
        locationInventoryService.deleteInventory(inventoryId, actorId, null);
        return ResponseEntity.noContent().build();
    }

    // ========= Storage location-level endpoints =========

    @GetMapping("/api/storage-locations/{storageLocationId}/inventory")
    public ResponseEntity<List<LocationInventoryResponseDTO>> listInventoryByStorageLocation(
            @PathVariable UUID storageLocationId) {
        List<LocationInventory> inventories = locationInventoryService
                .listInventoryByStorageLocation(storageLocationId);
        return ResponseEntity.ok(locationInventoryMapper.toResponseDTOList(inventories));
    }

}
