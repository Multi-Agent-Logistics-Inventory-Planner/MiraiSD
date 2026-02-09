package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.BoxBinMapper;
import com.mirai.inventoryservice.dtos.requests.BoxBinRequestDTO;
import com.mirai.inventoryservice.dtos.responses.BoxBinResponseDTO;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.services.BoxBinService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/box-bins")
public class BoxBinController {
    private final BoxBinService boxBinService;
    private final BoxBinMapper boxBinMapper;

    public BoxBinController(BoxBinService boxBinService, BoxBinMapper boxBinMapper) {
        this.boxBinService = boxBinService;
        this.boxBinMapper = boxBinMapper;
    }

    @GetMapping
    public ResponseEntity<List<BoxBinResponseDTO>> getAllBoxBins() {
        List<BoxBin> boxBins = boxBinService.getAllBoxBins();
        return ResponseEntity.ok(boxBinMapper.toResponseDTOList(boxBins));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoxBinResponseDTO> getBoxBinById(@PathVariable UUID id) {
        BoxBin boxBin = boxBinService.getBoxBinById(id);
        return ResponseEntity.ok(boxBinMapper.toResponseDTO(boxBin));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BoxBinResponseDTO> createBoxBin(@Valid @RequestBody BoxBinRequestDTO requestDTO) {
        BoxBin boxBin = boxBinService.createBoxBin(requestDTO.getBoxBinCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(boxBinMapper.toResponseDTO(boxBin));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BoxBinResponseDTO> updateBoxBin(
            @PathVariable UUID id,
            @Valid @RequestBody BoxBinRequestDTO requestDTO) {
        BoxBin boxBin = boxBinService.updateBoxBin(id, requestDTO.getBoxBinCode());
        return ResponseEntity.ok(boxBinMapper.toResponseDTO(boxBin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBoxBin(@PathVariable UUID id) {
        boxBinService.deleteBoxBin(id);
        return ResponseEntity.noContent().build();
    }
}

