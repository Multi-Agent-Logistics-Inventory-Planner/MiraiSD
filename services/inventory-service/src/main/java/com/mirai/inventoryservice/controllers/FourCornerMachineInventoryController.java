package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.FourCornerMachineInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.FourCornerMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.FourCornerMachineInventory;
import com.mirai.inventoryservice.services.FourCornerMachineInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/four-corner-machines/{fourCornerMachineId}/inventory")
public class FourCornerMachineInventoryController {
    private final FourCornerMachineInventoryService fourCornerMachineInventoryService;
    private final FourCornerMachineInventoryMapper fourCornerMachineInventoryMapper;

    public FourCornerMachineInventoryController(
            FourCornerMachineInventoryService fourCornerMachineInventoryService,
            FourCornerMachineInventoryMapper fourCornerMachineInventoryMapper) {
        this.fourCornerMachineInventoryService = fourCornerMachineInventoryService;
        this.fourCornerMachineInventoryMapper = fourCornerMachineInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<FourCornerMachineInventoryResponseDTO>> listInventory(
            @PathVariable UUID fourCornerMachineId) {
        List<FourCornerMachineInventory> inventories =
                fourCornerMachineInventoryService.listInventory(fourCornerMachineId);
        return ResponseEntity.ok(fourCornerMachineInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<FourCornerMachineInventoryResponseDTO> getInventoryById(
            @PathVariable UUID fourCornerMachineId,
            @PathVariable UUID inventoryId) {
        FourCornerMachineInventory inventory = fourCornerMachineInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(fourCornerMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    public ResponseEntity<FourCornerMachineInventoryResponseDTO> addInventory(
            @PathVariable UUID fourCornerMachineId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        FourCornerMachineInventory inventory = fourCornerMachineInventoryService.addInventory(
                fourCornerMachineId,
                requestDTO.getItemId(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fourCornerMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<FourCornerMachineInventoryResponseDTO> updateInventory(
            @PathVariable UUID fourCornerMachineId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        FourCornerMachineInventory inventory = fourCornerMachineInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(fourCornerMachineInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID fourCornerMachineId,
            @PathVariable UUID inventoryId) {
        fourCornerMachineInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}
