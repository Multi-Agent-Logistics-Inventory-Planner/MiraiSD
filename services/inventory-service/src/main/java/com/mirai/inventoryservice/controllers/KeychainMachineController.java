package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.KeychainMachineMapper;
import com.mirai.inventoryservice.dtos.requests.KeychainMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.KeychainMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.KeychainMachine;
import com.mirai.inventoryservice.services.KeychainMachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/keychain-machines")
public class KeychainMachineController {
    private final KeychainMachineService keychainMachineService;
    private final KeychainMachineMapper keychainMachineMapper;

    public KeychainMachineController(
            KeychainMachineService keychainMachineService,
            KeychainMachineMapper keychainMachineMapper) {
        this.keychainMachineService = keychainMachineService;
        this.keychainMachineMapper = keychainMachineMapper;
    }

    @GetMapping
    public ResponseEntity<List<KeychainMachineResponseDTO>> getAllKeychainMachines() {
        List<KeychainMachine> machines = keychainMachineService.getAllKeychainMachines();
        return ResponseEntity.ok(keychainMachineMapper.toResponseDTOList(machines));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KeychainMachineResponseDTO> getKeychainMachineById(@PathVariable UUID id) {
        KeychainMachine machine = keychainMachineService.getKeychainMachineById(id);
        return ResponseEntity.ok(keychainMachineMapper.toResponseDTO(machine));
    }

    @PostMapping
    public ResponseEntity<KeychainMachineResponseDTO> createKeychainMachine(
            @Valid @RequestBody KeychainMachineRequestDTO requestDTO) {
        KeychainMachine machine = keychainMachineService.createKeychainMachine(
                requestDTO.getKeychainMachineCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(keychainMachineMapper.toResponseDTO(machine));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KeychainMachineResponseDTO> updateKeychainMachine(
            @PathVariable UUID id,
            @Valid @RequestBody KeychainMachineRequestDTO requestDTO) {
        KeychainMachine machine = keychainMachineService.updateKeychainMachine(
                id, requestDTO.getKeychainMachineCode());
        return ResponseEntity.ok(keychainMachineMapper.toResponseDTO(machine));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeychainMachine(@PathVariable UUID id) {
        keychainMachineService.deleteKeychainMachine(id);
        return ResponseEntity.noContent().build();
    }
}

