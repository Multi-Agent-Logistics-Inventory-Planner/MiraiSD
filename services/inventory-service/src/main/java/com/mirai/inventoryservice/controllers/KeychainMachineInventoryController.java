package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.KeychainMachineInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.KeychainMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import com.mirai.inventoryservice.services.KeychainMachineInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/keychain-machines/{keychainMachineId}/inventory")
public class KeychainMachineInventoryController {
    private final KeychainMachineInventoryService keychainMachineInventoryService;
    private final KeychainMachineInventoryMapper keychainMachineInventoryMapper;

    public KeychainMachineInventoryController(
            KeychainMachineInventoryService keychainMachineInventoryService,
            KeychainMachineInventoryMapper keychainMachineInventoryMapper) {
        this.keychainMachineInventoryService = keychainMachineInventoryService;
        this.keychainMachineInventoryMapper = keychainMachineInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<KeychainMachineInventoryResponseDTO>> listInventory(
            @PathVariable UUID keychainMachineId) {
        List<KeychainMachineInventory> inventories = 
                keychainMachineInventoryService.listInventory(keychainMachineId);
        return ResponseEntity.ok(keychainMachineInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<KeychainMachineInventoryResponseDTO> getInventoryById(
            @PathVariable UUID keychainMachineId,
            @PathVariable UUID inventoryId) {
        KeychainMachineInventory inventory = keychainMachineInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(keychainMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    public ResponseEntity<KeychainMachineInventoryResponseDTO> addInventory(
            @PathVariable UUID keychainMachineId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        KeychainMachineInventory inventory = keychainMachineInventoryService.addInventory(
                keychainMachineId,
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getDescription(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(keychainMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<KeychainMachineInventoryResponseDTO> updateInventory(
            @PathVariable UUID keychainMachineId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        KeychainMachineInventory inventory = keychainMachineInventoryService.updateInventory(
                inventoryId,
                requestDTO.getCategory(),
                requestDTO.getSubcategory(),
                requestDTO.getDescription(),
                requestDTO.getQuantity());
        return ResponseEntity.ok(keychainMachineInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID keychainMachineId,
            @PathVariable UUID inventoryId) {
        keychainMachineInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}

