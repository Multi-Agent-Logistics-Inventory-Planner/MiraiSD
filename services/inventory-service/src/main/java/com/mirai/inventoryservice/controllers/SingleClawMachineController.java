package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.SingleClawMachineMapper;
import com.mirai.inventoryservice.dtos.requests.SingleClawMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.SingleClawMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import com.mirai.inventoryservice.services.SingleClawMachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/single-claw-machines")
public class SingleClawMachineController {
    private final SingleClawMachineService singleClawMachineService;
    private final SingleClawMachineMapper singleClawMachineMapper;

    public SingleClawMachineController(
            SingleClawMachineService singleClawMachineService,
            SingleClawMachineMapper singleClawMachineMapper) {
        this.singleClawMachineService = singleClawMachineService;
        this.singleClawMachineMapper = singleClawMachineMapper;
    }

    @GetMapping
    public ResponseEntity<List<SingleClawMachineResponseDTO>> getAllSingleClawMachines() {
        List<SingleClawMachine> machines = singleClawMachineService.getAllSingleClawMachines();
        return ResponseEntity.ok(singleClawMachineMapper.toResponseDTOList(machines));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SingleClawMachineResponseDTO> getSingleClawMachineById(@PathVariable UUID id) {
        SingleClawMachine machine = singleClawMachineService.getSingleClawMachineById(id);
        return ResponseEntity.ok(singleClawMachineMapper.toResponseDTO(machine));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SingleClawMachineResponseDTO> createSingleClawMachine(
            @Valid @RequestBody SingleClawMachineRequestDTO requestDTO) {
        SingleClawMachine machine = singleClawMachineService.createSingleClawMachine(
                requestDTO.getSingleClawMachineCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(singleClawMachineMapper.toResponseDTO(machine));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SingleClawMachineResponseDTO> updateSingleClawMachine(
            @PathVariable UUID id,
            @Valid @RequestBody SingleClawMachineRequestDTO requestDTO) {
        SingleClawMachine machine = singleClawMachineService.updateSingleClawMachine(
                id, requestDTO.getSingleClawMachineCode());
        return ResponseEntity.ok(singleClawMachineMapper.toResponseDTO(machine));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSingleClawMachine(@PathVariable UUID id) {
        singleClawMachineService.deleteSingleClawMachine(id);
        return ResponseEntity.noContent().build();
    }
}

