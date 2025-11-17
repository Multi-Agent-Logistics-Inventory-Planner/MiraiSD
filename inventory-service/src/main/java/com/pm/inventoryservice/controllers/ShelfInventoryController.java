package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.ShelfInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.ShelfInventoryResponseDTO;
import com.pm.inventoryservice.services.ShelfInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shelves/{shelfId}/inventory")
public class ShelfInventoryController {

    private final ShelfInventoryService shelfInventoryService;

    public ShelfInventoryController(ShelfInventoryService shelfInventoryService) {
        this.shelfInventoryService = shelfInventoryService;
    }

    @GetMapping
    public ResponseEntity<List<ShelfInventoryResponseDTO>> getInventoryByShelf(
            @PathVariable @NonNull UUID shelfId) {
        List<ShelfInventoryResponseDTO> inventory = shelfInventoryService.getInventoryByShelf(shelfId);
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<ShelfInventoryResponseDTO> getInventoryItem(
            @PathVariable @NonNull UUID shelfId,
            @PathVariable @NonNull UUID inventoryId) {
        ShelfInventoryResponseDTO inventory = shelfInventoryService.getInventoryItem(shelfId, inventoryId);
        return ResponseEntity.ok(inventory);
    }

    @PostMapping
    public ResponseEntity<ShelfInventoryResponseDTO> addInventory(
            @PathVariable @NonNull UUID shelfId,
            @Valid @RequestBody @NonNull ShelfInventoryRequestDTO request) {
        ShelfInventoryResponseDTO inventory = shelfInventoryService.addInventory(shelfId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventory);
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<ShelfInventoryResponseDTO> updateInventory(
            @PathVariable @NonNull UUID shelfId,
            @PathVariable @NonNull UUID inventoryId,
            @Valid @RequestBody @NonNull ShelfInventoryRequestDTO request) {
        ShelfInventoryResponseDTO inventory = shelfInventoryService.updateInventory(shelfId, inventoryId, request);
        return ResponseEntity.ok(inventory);
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable @NonNull UUID shelfId,
            @PathVariable @NonNull UUID inventoryId) {
        shelfInventoryService.deleteInventory(shelfId, inventoryId);
        return ResponseEntity.noContent().build();
    }
}

