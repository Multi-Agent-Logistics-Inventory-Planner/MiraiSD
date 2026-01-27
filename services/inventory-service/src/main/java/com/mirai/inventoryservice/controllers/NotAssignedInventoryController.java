package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.NotAssignedInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.NotAssignedInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.NotAssignedInventory;
import com.mirai.inventoryservice.services.NotAssignedInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/not-assigned/inventory")
public class NotAssignedInventoryController {
    private final NotAssignedInventoryService notAssignedInventoryService;
    private final NotAssignedInventoryMapper notAssignedInventoryMapper;

    public NotAssignedInventoryController(
            NotAssignedInventoryService notAssignedInventoryService,
            NotAssignedInventoryMapper notAssignedInventoryMapper) {
        this.notAssignedInventoryService = notAssignedInventoryService;
        this.notAssignedInventoryMapper = notAssignedInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<NotAssignedInventoryResponseDTO>> listInventory() {
        List<NotAssignedInventory> inventories = notAssignedInventoryService.listInventory();
        return ResponseEntity.ok(notAssignedInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<NotAssignedInventoryResponseDTO> getInventoryById(
            @PathVariable UUID inventoryId) {
        NotAssignedInventory inventory = notAssignedInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(notAssignedInventoryMapper.toResponseDTO(inventory));
    }

    @GetMapping("/by-product/{productId}")
    public ResponseEntity<List<NotAssignedInventoryResponseDTO>> getByProduct(
            @PathVariable UUID productId) {
        List<NotAssignedInventory> inventories = notAssignedInventoryService.findByProduct(productId);
        return ResponseEntity.ok(notAssignedInventoryMapper.toResponseDTOList(inventories));
    }

    @PostMapping
    public ResponseEntity<NotAssignedInventoryResponseDTO> addInventory(
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        NotAssignedInventory inventory = notAssignedInventoryService.addInventory(
                requestDTO.getItemId(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notAssignedInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<NotAssignedInventoryResponseDTO> updateInventory(
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        NotAssignedInventory inventory = notAssignedInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(notAssignedInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(@PathVariable UUID inventoryId) {
        notAssignedInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}
