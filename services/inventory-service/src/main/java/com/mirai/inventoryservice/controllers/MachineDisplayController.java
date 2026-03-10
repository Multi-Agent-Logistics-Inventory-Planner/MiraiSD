package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.BatchDisplaySwapRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayBatchRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SetMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.requests.SwapMachineDisplayRequestDTO;
import com.mirai.inventoryservice.dtos.responses.MachineDisplayDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.services.MachineDisplayService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/machine-displays")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class MachineDisplayController {
    private final MachineDisplayService machineDisplayService;

    public MachineDisplayController(MachineDisplayService machineDisplayService) {
        this.machineDisplayService = machineDisplayService;
    }

    /**
     * Set or swap the display for a machine
     */
    @PostMapping
    public ResponseEntity<MachineDisplayDTO> setDisplay(
            @Valid @RequestBody SetMachineDisplayRequestDTO request) {
        MachineDisplay display = machineDisplayService.setDisplay(request);
        MachineDisplayDTO dto = machineDisplayService.getCurrentDisplay(
                display.getLocationType(), display.getMachineId()).orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Add multiple products to a machine's display in a single request.
     * Products already displayed are skipped (no error).
     */
    @PostMapping("/batch")
    public ResponseEntity<List<MachineDisplayDTO>> setDisplayBatch(
            @Valid @RequestBody SetMachineDisplayBatchRequestDTO request) {
        List<MachineDisplay> displays = machineDisplayService.setDisplayBatch(request);
        List<MachineDisplayDTO> dtos = machineDisplayService.getActiveDisplaysForMachine(
                request.getLocationType(), request.getMachineId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dtos);
    }

    /**
     * Atomically swap one displayed product for another on the same machine.
     */
    @PostMapping("/swap")
    public ResponseEntity<List<MachineDisplayDTO>> swapDisplay(
            @Valid @RequestBody SwapMachineDisplayRequestDTO request) {
        List<MachineDisplayDTO> updatedDisplays = machineDisplayService.swapDisplay(request);
        return ResponseEntity.ok(updatedDisplays);
    }

    /**
     * Batch display swap operation that handles both swap modes:
     * 1. Swap with products - remove displays and add new products
     * 2. Swap with another machine - trade displays between two machines
     * Creates a single audit log entry with all changes.
     */
    @PostMapping("/batch-swap")
    public ResponseEntity<List<MachineDisplayDTO>> batchSwapDisplay(
            @Valid @RequestBody BatchDisplaySwapRequestDTO request) {
        List<MachineDisplayDTO> updatedDisplays = machineDisplayService.batchSwapDisplay(request);
        return ResponseEntity.ok(updatedDisplays);
    }

    /**
     * Clear all displays for a machine (end without setting new)
     */
    @DeleteMapping("/{locationType}/{machineId}")
    public ResponseEntity<Void> clearDisplay(
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId,
            @RequestParam(required = false) UUID actorId) {
        machineDisplayService.clearDisplay(locationType, machineId, actorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clear a specific display by ID
     */
    @DeleteMapping("/by-id/{displayId}")
    public ResponseEntity<Void> clearDisplayById(
            @PathVariable UUID displayId,
            @RequestParam(required = false) UUID actorId) {
        machineDisplayService.clearDisplayById(displayId, actorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all active displays for a specific machine
     */
    @GetMapping("/{locationType}/{machineId}/active")
    public ResponseEntity<List<MachineDisplayDTO>> getActiveDisplaysForMachine(
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        return ResponseEntity.ok(machineDisplayService.getActiveDisplaysForMachine(locationType, machineId));
    }

    /**
     * Get current display for a specific machine
     */
    @GetMapping("/{locationType}/{machineId}")
    public ResponseEntity<MachineDisplayDTO> getCurrentDisplay(
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        return machineDisplayService.getCurrentDisplay(locationType, machineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active displays
     */
    @GetMapping
    public ResponseEntity<List<MachineDisplayDTO>> getAllActiveDisplays() {
        return ResponseEntity.ok(machineDisplayService.getAllActiveDisplays());
    }

    /**
     * Get all active displays with pagination
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<MachineDisplayDTO>> getAllActiveDisplaysPaged(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(machineDisplayService.getAllActiveDisplays(pageable));
    }

    /**
     * Get active displays for a location type
     */
    @GetMapping("/by-type/{locationType}")
    public ResponseEntity<List<MachineDisplayDTO>> getActiveDisplaysByType(
            @PathVariable LocationType locationType) {
        return ResponseEntity.ok(machineDisplayService.getActiveDisplaysByLocationType(locationType));
    }

    /**
     * Get stale displays (active longer than threshold)
     */
    @GetMapping("/stale")
    public ResponseEntity<List<MachineDisplayDTO>> getStaleDisplays(
            @RequestParam(required = false) Integer thresholdDays) {
        if (thresholdDays != null) {
            return ResponseEntity.ok(machineDisplayService.getStaleDisplays(thresholdDays));
        }
        return ResponseEntity.ok(machineDisplayService.getStaleDisplays());
    }

    /**
     * Get stale displays for a location type
     */
    @GetMapping("/stale/{locationType}")
    public ResponseEntity<List<MachineDisplayDTO>> getStaleDisplaysByType(
            @PathVariable LocationType locationType) {
        return ResponseEntity.ok(machineDisplayService.getStaleDisplaysByLocationType(locationType));
    }

    /**
     * Get display history for a machine
     */
    @GetMapping("/{locationType}/{machineId}/history")
    public ResponseEntity<List<MachineDisplayDTO>> getMachineHistory(
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId) {
        return ResponseEntity.ok(machineDisplayService.getMachineHistory(locationType, machineId));
    }

    /**
     * Get display history for a machine with pagination
     */
    @GetMapping("/{locationType}/{machineId}/history/paged")
    public ResponseEntity<Page<MachineDisplayDTO>> getMachineHistoryPaged(
            @PathVariable LocationType locationType,
            @PathVariable UUID machineId,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(machineDisplayService.getMachineHistory(locationType, machineId, pageable));
    }

    /**
     * Get display history for a product
     */
    @GetMapping("/product/{productId}/history")
    public ResponseEntity<List<MachineDisplayDTO>> getProductHistory(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(machineDisplayService.getProductHistory(productId));
    }
}
