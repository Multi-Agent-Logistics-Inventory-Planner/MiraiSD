package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.DoubleClawMachineInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.DoubleClawMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import com.mirai.inventoryservice.services.DoubleClawMachineInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/double-claw-machines/{doubleClawMachineId}/inventory")
public class DoubleClawMachineInventoryController {
    private final DoubleClawMachineInventoryService doubleClawMachineInventoryService;
    private final DoubleClawMachineInventoryMapper doubleClawMachineInventoryMapper;

    public DoubleClawMachineInventoryController(
            DoubleClawMachineInventoryService doubleClawMachineInventoryService,
            DoubleClawMachineInventoryMapper doubleClawMachineInventoryMapper) {
        this.doubleClawMachineInventoryService = doubleClawMachineInventoryService;
        this.doubleClawMachineInventoryMapper = doubleClawMachineInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<DoubleClawMachineInventoryResponseDTO>> listInventory(
            @PathVariable UUID doubleClawMachineId) {
        List<DoubleClawMachineInventory> inventories = 
                doubleClawMachineInventoryService.listInventory(doubleClawMachineId);
        return ResponseEntity.ok(doubleClawMachineInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<DoubleClawMachineInventoryResponseDTO> getInventoryById(
            @PathVariable UUID doubleClawMachineId,
            @PathVariable UUID inventoryId) {
        DoubleClawMachineInventory inventory = doubleClawMachineInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(doubleClawMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    public ResponseEntity<DoubleClawMachineInventoryResponseDTO> addInventory(
            @PathVariable UUID doubleClawMachineId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        DoubleClawMachineInventory inventory = doubleClawMachineInventoryService.addInventory(
                doubleClawMachineId,
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getDescription(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doubleClawMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<DoubleClawMachineInventoryResponseDTO> updateInventory(
            @PathVariable UUID doubleClawMachineId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        DoubleClawMachineInventory inventory = doubleClawMachineInventoryService.updateInventory(
                inventoryId,
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getDescription(),
                requestDTO.getQuantity());
        return ResponseEntity.ok(doubleClawMachineInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID doubleClawMachineId,
            @PathVariable UUID inventoryId) {
        doubleClawMachineInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

