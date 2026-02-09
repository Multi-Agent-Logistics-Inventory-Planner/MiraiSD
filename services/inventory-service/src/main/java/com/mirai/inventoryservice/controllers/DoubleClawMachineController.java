package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.DoubleClawMachineMapper;
import com.mirai.inventoryservice.dtos.requests.DoubleClawMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.DoubleClawMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
import com.mirai.inventoryservice.services.DoubleClawMachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/double-claw-machines")
public class DoubleClawMachineController {
    private final DoubleClawMachineService doubleClawMachineService;
    private final DoubleClawMachineMapper doubleClawMachineMapper;

    public DoubleClawMachineController(
            DoubleClawMachineService doubleClawMachineService,
            DoubleClawMachineMapper doubleClawMachineMapper) {
        this.doubleClawMachineService = doubleClawMachineService;
        this.doubleClawMachineMapper = doubleClawMachineMapper;
    }

    @GetMapping
    public ResponseEntity<List<DoubleClawMachineResponseDTO>> getAllDoubleClawMachines() {
        List<DoubleClawMachine> machines = doubleClawMachineService.getAllDoubleClawMachines();
        return ResponseEntity.ok(doubleClawMachineMapper.toResponseDTOList(machines));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoubleClawMachineResponseDTO> getDoubleClawMachineById(@PathVariable UUID id) {
        DoubleClawMachine machine = doubleClawMachineService.getDoubleClawMachineById(id);
        return ResponseEntity.ok(doubleClawMachineMapper.toResponseDTO(machine));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoubleClawMachineResponseDTO> createDoubleClawMachine(
            @Valid @RequestBody DoubleClawMachineRequestDTO requestDTO) {
        DoubleClawMachine machine = doubleClawMachineService.createDoubleClawMachine(
                requestDTO.getDoubleClawMachineCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doubleClawMachineMapper.toResponseDTO(machine));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoubleClawMachineResponseDTO> updateDoubleClawMachine(
            @PathVariable UUID id,
            @Valid @RequestBody DoubleClawMachineRequestDTO requestDTO) {
        DoubleClawMachine machine = doubleClawMachineService.updateDoubleClawMachine(
                id, requestDTO.getDoubleClawMachineCode());
        return ResponseEntity.ok(doubleClawMachineMapper.toResponseDTO(machine));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDoubleClawMachine(@PathVariable UUID id) {
        doubleClawMachineService.deleteDoubleClawMachine(id);
        return ResponseEntity.noContent().build();
    }
}

