package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.SingleClawMachineInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.SingleClawMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import com.mirai.inventoryservice.services.SingleClawMachineInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/single-claw-machines/{singleClawMachineId}/inventory")
public class SingleClawMachineInventoryController {
    private final SingleClawMachineInventoryService singleClawMachineInventoryService;
    private final SingleClawMachineInventoryMapper singleClawMachineInventoryMapper;

    public SingleClawMachineInventoryController(
            SingleClawMachineInventoryService singleClawMachineInventoryService,
            SingleClawMachineInventoryMapper singleClawMachineInventoryMapper) {
        this.singleClawMachineInventoryService = singleClawMachineInventoryService;
        this.singleClawMachineInventoryMapper = singleClawMachineInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<SingleClawMachineInventoryResponseDTO>> listInventory(
            @PathVariable UUID singleClawMachineId) {
        List<SingleClawMachineInventory> inventories = 
                singleClawMachineInventoryService.listInventory(singleClawMachineId);
        return ResponseEntity.ok(singleClawMachineInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<SingleClawMachineInventoryResponseDTO> getInventoryById(
            @PathVariable UUID singleClawMachineId,
            @PathVariable UUID inventoryId) {
        SingleClawMachineInventory inventory = singleClawMachineInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(singleClawMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SingleClawMachineInventoryResponseDTO> addInventory(
            @PathVariable UUID singleClawMachineId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        SingleClawMachineInventory inventory = singleClawMachineInventoryService.addInventory(
                singleClawMachineId,
                requestDTO.getItemId(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(singleClawMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SingleClawMachineInventoryResponseDTO> updateInventory(
            @PathVariable UUID singleClawMachineId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        SingleClawMachineInventory inventory = singleClawMachineInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(singleClawMachineInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID singleClawMachineId,
            @PathVariable UUID inventoryId) {
        singleClawMachineInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

