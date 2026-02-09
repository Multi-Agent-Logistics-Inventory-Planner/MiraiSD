package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.RackMapper;
import com.mirai.inventoryservice.dtos.requests.RackRequestDTO;
import com.mirai.inventoryservice.dtos.responses.RackResponseDTO;
import com.mirai.inventoryservice.models.storage.Rack;
import com.mirai.inventoryservice.services.RackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/racks")
public class RackController {
    private final RackService rackService;
    private final RackMapper rackMapper;

    public RackController(RackService rackService, RackMapper rackMapper) {
        this.rackService = rackService;
        this.rackMapper = rackMapper;
    }

    @GetMapping
    public ResponseEntity<List<RackResponseDTO>> getAllRacks() {
        List<Rack> racks = rackService.getAllRacks();
        return ResponseEntity.ok(rackMapper.toResponseDTOList(racks));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RackResponseDTO> getRackById(@PathVariable UUID id) {
        Rack rack = rackService.getRackById(id);
        return ResponseEntity.ok(rackMapper.toResponseDTO(rack));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RackResponseDTO> createRack(@Valid @RequestBody RackRequestDTO requestDTO) {
        Rack rack = rackService.createRack(requestDTO.getRackCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(rackMapper.toResponseDTO(rack));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RackResponseDTO> updateRack(
            @PathVariable UUID id,
            @Valid @RequestBody RackRequestDTO requestDTO) {
        Rack rack = rackService.updateRack(id, requestDTO.getRackCode());
        return ResponseEntity.ok(rackMapper.toResponseDTO(rack));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRack(@PathVariable UUID id) {
        rackService.deleteRack(id);
        return ResponseEntity.noContent().build();
    }
}

