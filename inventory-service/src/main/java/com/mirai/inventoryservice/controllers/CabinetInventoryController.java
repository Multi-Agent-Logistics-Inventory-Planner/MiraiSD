package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.CabinetInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CabinetInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import com.mirai.inventoryservice.services.CabinetInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cabinets/{cabinetId}/inventory")
public class CabinetInventoryController {
    private final CabinetInventoryService cabinetInventoryService;
    private final CabinetInventoryMapper cabinetInventoryMapper;

    public CabinetInventoryController(
            CabinetInventoryService cabinetInventoryService,
            CabinetInventoryMapper cabinetInventoryMapper) {
        this.cabinetInventoryService = cabinetInventoryService;
        this.cabinetInventoryMapper = cabinetInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<CabinetInventoryResponseDTO>> listInventory(@PathVariable UUID cabinetId) {
        List<CabinetInventory> inventories = cabinetInventoryService.listInventory(cabinetId);
        return ResponseEntity.ok(cabinetInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<CabinetInventoryResponseDTO> getInventoryById(
            @PathVariable UUID cabinetId,
            @PathVariable UUID inventoryId) {
        CabinetInventory inventory = cabinetInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(cabinetInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    public ResponseEntity<CabinetInventoryResponseDTO> addInventory(
            @PathVariable UUID cabinetId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        CabinetInventory inventory = cabinetInventoryService.addInventory(
                cabinetId,
                requestDTO.getItemId(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cabinetInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<CabinetInventoryResponseDTO> updateInventory(
            @PathVariable UUID cabinetId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        CabinetInventory inventory = cabinetInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(cabinetInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID cabinetId,
            @PathVariable UUID inventoryId) {
        cabinetInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

