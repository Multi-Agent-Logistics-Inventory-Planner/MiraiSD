package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.PusherMachineInventoryMapper;
import com.mirai.inventoryservice.dtos.requests.InventoryRequestDTO;
import com.mirai.inventoryservice.dtos.responses.PusherMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.PusherMachineInventory;
import com.mirai.inventoryservice.services.PusherMachineInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pusher-machines/{pusherMachineId}/inventory")
public class PusherMachineInventoryController {
    private final PusherMachineInventoryService pusherMachineInventoryService;
    private final PusherMachineInventoryMapper pusherMachineInventoryMapper;

    public PusherMachineInventoryController(
            PusherMachineInventoryService pusherMachineInventoryService,
            PusherMachineInventoryMapper pusherMachineInventoryMapper) {
        this.pusherMachineInventoryService = pusherMachineInventoryService;
        this.pusherMachineInventoryMapper = pusherMachineInventoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<PusherMachineInventoryResponseDTO>> listInventory(
            @PathVariable UUID pusherMachineId) {
        List<PusherMachineInventory> inventories =
                pusherMachineInventoryService.listInventory(pusherMachineId);
        return ResponseEntity.ok(pusherMachineInventoryMapper.toResponseDTOList(inventories));
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<PusherMachineInventoryResponseDTO> getInventoryById(
            @PathVariable UUID pusherMachineId,
            @PathVariable UUID inventoryId) {
        PusherMachineInventory inventory = pusherMachineInventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(pusherMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PostMapping
    public ResponseEntity<PusherMachineInventoryResponseDTO> addInventory(
            @PathVariable UUID pusherMachineId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        PusherMachineInventory inventory = pusherMachineInventoryService.addInventory(
                pusherMachineId,
                requestDTO.getItemId(),
                requestDTO.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pusherMachineInventoryMapper.toResponseDTO(inventory));
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<PusherMachineInventoryResponseDTO> updateInventory(
            @PathVariable UUID pusherMachineId,
            @PathVariable UUID inventoryId,
            @Valid @RequestBody InventoryRequestDTO requestDTO) {
        PusherMachineInventory inventory = pusherMachineInventoryService.updateInventory(
                inventoryId,
                requestDTO.getQuantity());
        return ResponseEntity.ok(pusherMachineInventoryMapper.toResponseDTO(inventory));
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable UUID pusherMachineId,
            @PathVariable UUID inventoryId) {
        pusherMachineInventoryService.deleteInventory(inventoryId);
        return ResponseEntity.noContent().build();
    }
}
