package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.PusherMachineMapper;
import com.mirai.inventoryservice.dtos.requests.PusherMachineRequestDTO;
import com.mirai.inventoryservice.dtos.responses.PusherMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.PusherMachine;
import com.mirai.inventoryservice.services.PusherMachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pusher-machines")
public class PusherMachineController {
    private final PusherMachineService pusherMachineService;
    private final PusherMachineMapper pusherMachineMapper;

    public PusherMachineController(
            PusherMachineService pusherMachineService,
            PusherMachineMapper pusherMachineMapper) {
        this.pusherMachineService = pusherMachineService;
        this.pusherMachineMapper = pusherMachineMapper;
    }

    @GetMapping
    public ResponseEntity<List<PusherMachineResponseDTO>> getAllPusherMachines() {
        List<PusherMachine> machines = pusherMachineService.getAllPusherMachines();
        return ResponseEntity.ok(pusherMachineMapper.toResponseDTOList(machines));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PusherMachineResponseDTO> getPusherMachineById(@PathVariable UUID id) {
        PusherMachine machine = pusherMachineService.getPusherMachineById(id);
        return ResponseEntity.ok(pusherMachineMapper.toResponseDTO(machine));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PusherMachineResponseDTO> createPusherMachine(
            @Valid @RequestBody PusherMachineRequestDTO requestDTO) {
        PusherMachine machine = pusherMachineService.createPusherMachine(
                requestDTO.getPusherMachineCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pusherMachineMapper.toResponseDTO(machine));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PusherMachineResponseDTO> updatePusherMachine(
            @PathVariable UUID id,
            @Valid @RequestBody PusherMachineRequestDTO requestDTO) {
        PusherMachine machine = pusherMachineService.updatePusherMachine(
                id, requestDTO.getPusherMachineCode());
        return ResponseEntity.ok(pusherMachineMapper.toResponseDTO(machine));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePusherMachine(@PathVariable UUID id) {
        pusherMachineService.deletePusherMachine(id);
        return ResponseEntity.noContent().build();
    }
}
