package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.WindowMapper;
import com.mirai.inventoryservice.dtos.requests.WindowRequestDTO;
import com.mirai.inventoryservice.dtos.responses.WindowResponseDTO;
import com.mirai.inventoryservice.models.storage.Window;
import com.mirai.inventoryservice.services.WindowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/windows")
public class WindowController {
    private final WindowService windowService;
    private final WindowMapper windowMapper;

    public WindowController(WindowService windowService, WindowMapper windowMapper) {
        this.windowService = windowService;
        this.windowMapper = windowMapper;
    }

    @GetMapping
    public ResponseEntity<List<WindowResponseDTO>> getAllWindows() {
        List<Window> windows = windowService.getAllWindows();
        return ResponseEntity.ok(windowMapper.toResponseDTOList(windows));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WindowResponseDTO> getWindowById(@PathVariable UUID id) {
        Window window = windowService.getWindowById(id);
        return ResponseEntity.ok(windowMapper.toResponseDTO(window));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WindowResponseDTO> createWindow(@Valid @RequestBody WindowRequestDTO requestDTO) {
        Window window = windowService.createWindow(requestDTO.getWindowCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(windowMapper.toResponseDTO(window));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WindowResponseDTO> updateWindow(
            @PathVariable UUID id,
            @Valid @RequestBody WindowRequestDTO requestDTO) {
        Window window = windowService.updateWindow(id, requestDTO.getWindowCode());
        return ResponseEntity.ok(windowMapper.toResponseDTO(window));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWindow(@PathVariable UUID id) {
        windowService.deleteWindow(id);
        return ResponseEntity.noContent().build();
    }
}

