package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.WindowInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.WindowInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.WindowInventory;
import com.mirai.inventoryservice.services.WindowInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/windows/{windowId}/inventory")
public class WindowInventoryController {
    private final WindowInventoryService windowInventoryService;
    private final WindowInventoryMapper windowInventoryMapper;

    public WindowInventoryController(
            WindowInventoryService windowInventoryService,
            WindowInventoryMapper windowInventoryMapper) {
        this.windowInventoryService = windowInventoryService;
        this.windowInventoryMapper = windowInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<WindowInventoryResponseDTO>> listInventory(@PathVariable UUID windowId) {
        List<WindowInventory> inventories = windowInventoryService.listInventory(windowId);
        return ResponseEntity.ok(windowInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<WindowInventoryResponseDTO> getInventoryById(
            @PathVariable UUID windowId,
            @PathVariable UUID inventoryId) {
        WindowInventory inventory = windowInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(windowInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<WindowInventoryResponseDTO> addInventory(
            @PathVariable UUID windowId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        WindowInventory inventory = windowInventoryService.addInventory(
                windowId,
                requestDTO.getItemId(),
                requestDTO.getQuantity(),
                requestDTO.getActorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(windowInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<WindowInventoryResponseDTO> updateInventory(
            @PathVariable UUID windowId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        WindowInventory inventory = windowInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(windowInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID windowId,
            @PathVariable UUID inventoryId) {
        windowInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

