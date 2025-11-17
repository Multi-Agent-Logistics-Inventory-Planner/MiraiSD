package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.BinInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.BinInventoryResponseDTO;
import com.pm.inventoryservice.services.BinInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bins/{binId}/inventory")
public class BinInventoryController {

    private final BinInventoryService binInventoryService;

    public BinInventoryController(BinInventoryService binInventoryService) {
        this.binInventoryService = binInventoryService;
    }

    @GetMapping
    public ResponseEntity<List<BinInventoryResponseDTO>> getInventoryByBin(
            @PathVariable @NonNull UUID binId) {
        List<BinInventoryResponseDTO> inventory = binInventoryService.getInventoryByBin(binId);
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<BinInventoryResponseDTO> getInventoryItem(
            @PathVariable @NonNull UUID binId,
            @PathVariable @NonNull UUID inventoryId) {
        BinInventoryResponseDTO inventory = binInventoryService.getInventoryItem(binId, inventoryId);
        return ResponseEntity.ok(inventory);
    }

    @PostMapping
    public ResponseEntity<BinInventoryResponseDTO> addInventory(
            @PathVariable @NonNull UUID binId,
            @Valid @RequestBody @NonNull BinInventoryRequestDTO request) {
        BinInventoryResponseDTO inventory = binInventoryService.addInventory(binId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventory);
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<BinInventoryResponseDTO> updateInventory(
            @PathVariable @NonNull UUID binId,
            @PathVariable @NonNull UUID inventoryId,
            @Valid @RequestBody @NonNull BinInventoryRequestDTO request) {
        BinInventoryResponseDTO inventory = binInventoryService.updateInventory(binId, inventoryId, request);
        return ResponseEntity.ok(inventory);
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable @NonNull UUID binId,
            @PathVariable @NonNull UUID inventoryId) {
        binInventoryService.deleteInventory(binId, inventoryId);
        return ResponseEntity.noContent().build();
    }
}

