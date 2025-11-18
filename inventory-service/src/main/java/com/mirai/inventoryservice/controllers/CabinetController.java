package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.CabinetMapper;
import com.mirai.inventoryservice.dtos.requests.CabinetRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CabinetResponseDTO;
import com.mirai.inventoryservice.models.storage.Cabinet;
import com.mirai.inventoryservice.services.CabinetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cabinets")
public class CabinetController {
    private final CabinetService cabinetService;
    private final CabinetMapper cabinetMapper;

    public CabinetController(CabinetService cabinetService, CabinetMapper cabinetMapper) {
        this.cabinetService = cabinetService;
        this.cabinetMapper = cabinetMapper;
    }

    @GetMapping
    public ResponseEntity<List<CabinetResponseDTO>> getAllCabinets() {
        List<Cabinet> cabinets = cabinetService.getAllCabinets();
        return ResponseEntity.ok(cabinetMapper.toResponseDTOList(cabinets));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabinetResponseDTO> getCabinetById(@PathVariable UUID id) {
        Cabinet cabinet = cabinetService.getCabinetById(id);
        return ResponseEntity.ok(cabinetMapper.toResponseDTO(cabinet));
    }

    @PostMapping
    public ResponseEntity<CabinetResponseDTO> createCabinet(@Valid @RequestBody CabinetRequestDTO requestDTO) {
        Cabinet cabinet = cabinetService.createCabinet(requestDTO.getCabinetCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(cabinetMapper.toResponseDTO(cabinet));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CabinetResponseDTO> updateCabinet(
            @PathVariable UUID id,
            @Valid @RequestBody CabinetRequestDTO requestDTO) {
        Cabinet cabinet = cabinetService.updateCabinet(id, requestDTO.getCabinetCode());
        return ResponseEntity.ok(cabinetMapper.toResponseDTO(cabinet));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCabinet(@PathVariable UUID id) {
        cabinetService.deleteCabinet(id);
        return ResponseEntity.noContent().build();
    }
}

