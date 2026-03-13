package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.GachaponMapper;
import com.mirai.inventoryservice.dtos.requests.GachaponRequestDTO;
import com.mirai.inventoryservice.dtos.responses.GachaponResponseDTO;
import com.mirai.inventoryservice.models.storage.Gachapon;
import com.mirai.inventoryservice.services.GachaponService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gachapons")
public class GachaponController {
    private final GachaponService gachaponService;
    private final GachaponMapper gachaponMapper;

    public GachaponController(
            GachaponService gachaponService,
            GachaponMapper gachaponMapper) {
        this.gachaponService = gachaponService;
        this.gachaponMapper = gachaponMapper;
    }

    @GetMapping
    public ResponseEntity<List<GachaponResponseDTO>> getAllGachapons() {
        List<Gachapon> gachapons = gachaponService.getAllGachapons();
        return ResponseEntity.ok(gachaponMapper.toResponseDTOList(gachapons));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GachaponResponseDTO> getGachaponById(@PathVariable UUID id) {
        Gachapon gachapon = gachaponService.getGachaponById(id);
        return ResponseEntity.ok(gachaponMapper.toResponseDTO(gachapon));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GachaponResponseDTO> createGachapon(
            @Valid @RequestBody GachaponRequestDTO requestDTO) {
        Gachapon gachapon = gachaponService.createGachapon(
                requestDTO.getGachaponCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gachaponMapper.toResponseDTO(gachapon));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GachaponResponseDTO> updateGachapon(
            @PathVariable UUID id,
            @Valid @RequestBody GachaponRequestDTO requestDTO) {
        Gachapon gachapon = gachaponService.updateGachapon(
                id, requestDTO.getGachaponCode());
        return ResponseEntity.ok(gachaponMapper.toResponseDTO(gachapon));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGachapon(@PathVariable UUID id) {
        gachaponService.deleteGachapon(id);
        return ResponseEntity.noContent().build();
    }
}
