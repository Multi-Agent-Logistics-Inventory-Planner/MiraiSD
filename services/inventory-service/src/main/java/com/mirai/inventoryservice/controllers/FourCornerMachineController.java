package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.FourCornerMachineMapper;
import com.mirai.inventoryservice.dtos.requests.FourCornerMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.FourCornerMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.FourCornerMachine;
import com.mirai.inventoryservice.services.FourCornerMachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/four-corner-machines")
public class FourCornerMachineController {
    private final FourCornerMachineService fourCornerMachineService;
    private final FourCornerMachineMapper fourCornerMachineMapper;

    public FourCornerMachineController(
            FourCornerMachineService fourCornerMachineService,
            FourCornerMachineMapper fourCornerMachineMapper) {
        this.fourCornerMachineService = fourCornerMachineService;
        this.fourCornerMachineMapper = fourCornerMachineMapper;
    }

    @GetMapping
    public ResponseEntity<List<FourCornerMachineResponseDTO>> getAllFourCornerMachines() {
        List<FourCornerMachine> machines = fourCornerMachineService.getAllFourCornerMachines();
        return ResponseEntity.ok(fourCornerMachineMapper.toResponseDTOList(machines));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FourCornerMachineResponseDTO> getFourCornerMachineById(@PathVariable UUID id) {
        FourCornerMachine machine = fourCornerMachineService.getFourCornerMachineById(id);
        return ResponseEntity.ok(fourCornerMachineMapper.toResponseDTO(machine));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FourCornerMachineResponseDTO> createFourCornerMachine(
            @Valid @RequestBody FourCornerMachineRequestDTO requestDTO) {
        FourCornerMachine machine = fourCornerMachineService.createFourCornerMachine(
                requestDTO.getFourCornerMachineCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fourCornerMachineMapper.toResponseDTO(machine));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FourCornerMachineResponseDTO> updateFourCornerMachine(
            @PathVariable UUID id,
            @Valid @RequestBody FourCornerMachineRequestDTO requestDTO) {
        FourCornerMachine machine = fourCornerMachineService.updateFourCornerMachine(
                id, requestDTO.getFourCornerMachineCode());
        return ResponseEntity.ok(fourCornerMachineMapper.toResponseDTO(machine));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFourCornerMachine(@PathVariable UUID id) {
        fourCornerMachineService.deleteFourCornerMachine(id);
        return ResponseEntity.noContent().build();
    }
}
