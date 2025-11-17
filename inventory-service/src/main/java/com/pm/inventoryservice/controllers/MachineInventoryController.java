package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.MachineInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.MachineInventoryResponseDTO;
import com.pm.inventoryservice.services.MachineInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/machines/{machineId}/inventory")
public class MachineInventoryController {

    private final MachineInventoryService machineInventoryService;

    public MachineInventoryController(MachineInventoryService machineInventoryService) {
        this.machineInventoryService = machineInventoryService;
    }

    @GetMapping
    public ResponseEntity<List<MachineInventoryResponseDTO>> getInventoryByMachine(
            @PathVariable @NonNull UUID machineId) {
        List<MachineInventoryResponseDTO> inventory = machineInventoryService.getInventoryByMachine(machineId);
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<MachineInventoryResponseDTO> getInventoryItem(
            @PathVariable @NonNull UUID machineId,
            @PathVariable @NonNull UUID inventoryId) {
        MachineInventoryResponseDTO inventory = machineInventoryService.getInventoryItem(machineId, inventoryId);
        return ResponseEntity.ok(inventory);
    }

    @PostMapping
    public ResponseEntity<MachineInventoryResponseDTO> addInventory(
            @PathVariable @NonNull UUID machineId,
            @Valid @RequestBody @NonNull MachineInventoryRequestDTO request) {
        MachineInventoryResponseDTO inventory = machineInventoryService.addInventory(machineId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventory);
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<MachineInventoryResponseDTO> updateInventory(
            @PathVariable @NonNull UUID machineId,
            @PathVariable @NonNull UUID inventoryId,
            @Valid @RequestBody @NonNull MachineInventoryRequestDTO request) {
        MachineInventoryResponseDTO inventory = machineInventoryService.updateInventory(machineId, inventoryId, request);
        return ResponseEntity.ok(inventory);
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable @NonNull UUID machineId,
            @PathVariable @NonNull UUID inventoryId) {
        machineInventoryService.deleteInventory(machineId, inventoryId);
        return ResponseEntity.noContent().build();
    }
}
