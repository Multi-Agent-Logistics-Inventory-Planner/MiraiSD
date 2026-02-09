package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.RackInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.RackInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.RackInventory;
import com.mirai.inventoryservice.services.RackInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/racks/{rackId}/inventory")
public class RackInventoryController {
    private final RackInventoryService rackInventoryService;
    private final RackInventoryMapper rackInventoryMapper;

    public RackInventoryController(
            RackInventoryService rackInventoryService,
            RackInventoryMapper rackInventoryMapper) {
        this.rackInventoryService = rackInventoryService;
        this.rackInventoryMapper = rackInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<RackInventoryResponseDTO>> listInventory(@PathVariable UUID rackId) {
        List<RackInventory> inventories = rackInventoryService.listInventory(rackId);
        return ResponseEntity.ok(rackInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<RackInventoryResponseDTO> getInventoryById(
            @PathVariable UUID rackId,
            @PathVariable UUID inventoryId) {
        RackInventory inventory = rackInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(rackInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RackInventoryResponseDTO> addInventory(
            @PathVariable UUID rackId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        RackInventory inventory = rackInventoryService.addInventory(
                rackId,
                requestDTO.getItemId(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rackInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RackInventoryResponseDTO> updateInventory(
            @PathVariable UUID rackId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        RackInventory inventory = rackInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(rackInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID rackId,
            @PathVariable UUID inventoryId) {
        rackInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

