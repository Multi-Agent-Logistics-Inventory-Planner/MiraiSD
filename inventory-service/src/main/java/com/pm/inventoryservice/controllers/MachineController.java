package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.MachineRequestDTO;
import com.pm.inventoryservice.dtos.responses.MachineResponseDTO;
import com.pm.inventoryservice.dtos.validators.CreateMachineValidationGroup;
import com.pm.inventoryservice.services.MachineService;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/machines")
public class MachineController {
    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public ResponseEntity<List<MachineResponseDTO>> getAllMachines() {
        List<MachineResponseDTO> machines = machineService.getAllMachines();
        return ResponseEntity.ok().body(machines);
    }

    @PostMapping
    public ResponseEntity<MachineResponseDTO> createMachine(@Validated({Default.class, CreateMachineValidationGroup.class}) @RequestBody MachineRequestDTO machineRequestDTO){
        MachineResponseDTO machineResponseDTO = machineService.createMachine(machineRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(machineResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MachineResponseDTO> updateMachine(@PathVariable UUID id, @Validated({Default.class}) @RequestBody MachineRequestDTO machineRequestDTO){
        MachineResponseDTO machineResponseDTO = machineService.updateMachine(id, machineRequestDTO);
        return ResponseEntity.ok().body(machineResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMachine(@PathVariable UUID id){
        machineService.deleteMachine(id);
        return ResponseEntity.noContent().build();
    }
}
