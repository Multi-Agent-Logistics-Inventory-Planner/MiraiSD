package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.BoxBinInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.BoxBinInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.services.BoxBinInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/box-bins/{boxBinId}/inventory")
public class BoxBinInventoryController {
    private final BoxBinInventoryService boxBinInventoryService;
    private final BoxBinInventoryMapper boxBinInventoryMapper;

    public BoxBinInventoryController(
            BoxBinInventoryService boxBinInventoryService,
            BoxBinInventoryMapper boxBinInventoryMapper) {
        this.boxBinInventoryService = boxBinInventoryService;
        this.boxBinInventoryMapper = boxBinInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<BoxBinInventoryResponseDTO>> listInventory(@PathVariable UUID boxBinId) {
        List<BoxBinInventory> inventories = boxBinInventoryService.listInventory(boxBinId);
        return ResponseEntity.ok(boxBinInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<BoxBinInventoryResponseDTO> getInventoryById(
            @PathVariable UUID boxBinId,
            @PathVariable UUID inventoryId) {
        BoxBinInventory inventory = boxBinInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(boxBinInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    public ResponseEntity<BoxBinInventoryResponseDTO> addInventory(
            @PathVariable UUID boxBinId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        BoxBinInventory inventory = boxBinInventoryService.addInventory(
                boxBinId,
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getDescription(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(boxBinInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<BoxBinInventoryResponseDTO> updateInventory(
            @PathVariable UUID boxBinId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        BoxBinInventory inventory = boxBinInventoryService.updateInventory(
                inventoryId,
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getDescription(),
                requestDTO.getQuantity());
        return ResponseEntity.ok(boxBinInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID boxBinId,
            @PathVariable UUID inventoryId) {
        boxBinInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

