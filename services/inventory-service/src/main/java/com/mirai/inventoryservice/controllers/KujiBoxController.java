package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.kuji.AddKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.AddSlipRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.PatchKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.RecordDrawRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.TransferInMoreRequestDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiAllocationByLocationDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiAllocationByProductDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxTierResponseDTO;
import com.mirai.inventoryservice.services.KujiBoxService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for custom kuji box tracking.
 *
 * Reads ({@code GET}) are open to any authenticated user. Structural mutations
 * (open/close/reopen/edit/transfer-in) require ADMIN or ASSISTANT_MANAGER.
 * Draw/undo/add-slip are also available to EMPLOYEE because front-of-house
 * staff drive these.
 */
@RestController
@RequestMapping("/api/kuji-boxes")
public class KujiBoxController {

    private final KujiBoxService kujiBoxService;

    public KujiBoxController(KujiBoxService kujiBoxService) {
        this.kujiBoxService = kujiBoxService;
    }

    // ===================== Reads =====================

    @GetMapping("/by-product/{productId}/active")
    public ResponseEntity<KujiBoxResponseDTO> getActiveBoxByProduct(@PathVariable UUID productId) {
        KujiBoxResponseDTO box = kujiBoxService.getActiveBoxByProduct(productId);
        if (box == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(box);
    }

    @GetMapping("/by-product/{productId}/history")
    public ResponseEntity<List<KujiBoxResponseDTO>> getBoxHistory(@PathVariable UUID productId) {
        return ResponseEntity.ok(kujiBoxService.getBoxHistory(productId));
    }

    @GetMapping("/by-product/{productId}/last-tiers")
    public ResponseEntity<List<KujiBoxTierResponseDTO>> getLastClosedTiers(@PathVariable UUID productId) {
        return ResponseEntity.ok(kujiBoxService.cloneTiersFromLastClosedBox(productId));
    }

    @GetMapping("/{boxId}")
    public ResponseEntity<KujiBoxResponseDTO> getBox(@PathVariable UUID boxId) {
        return ResponseEntity.ok(kujiBoxService.getBox(boxId));
    }

    @GetMapping("/allocations/by-location/{locationId}")
    public ResponseEntity<List<KujiAllocationByLocationDTO>> getAllocationsByLocation(
            @PathVariable UUID locationId) {
        return ResponseEntity.ok(kujiBoxService.getAllocationsByLocation(locationId));
    }

    @GetMapping("/allocations/by-product/{productId}")
    public ResponseEntity<List<KujiAllocationByProductDTO>> getAllocationsByProduct(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(kujiBoxService.getAllocationsByProduct(productId));
    }

    // ===================== Lifecycle =====================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> openBox(@Valid @RequestBody OpenKujiBoxRequestDTO request) {
        KujiBoxResponseDTO created = kujiBoxService.openBox(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{boxId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> closeBox(
            @PathVariable UUID boxId,
            @Valid @RequestBody CloseKujiBoxRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.closeBox(boxId, request));
    }

    @PatchMapping("/{boxId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> reopenBox(
            @PathVariable UUID boxId,
            @RequestParam UUID actorId) {
        return ResponseEntity.ok(kujiBoxService.reopenBox(boxId, actorId));
    }

    // ===================== Tier mutations =====================

    @PostMapping("/{boxId}/tiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> addTier(
            @PathVariable UUID boxId,
            @Valid @RequestBody AddKujiTierRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.addTier(boxId, request));
    }

    @PatchMapping("/{boxId}/tiers/{tierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> patchTier(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody PatchKujiTierRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.patchTier(boxId, tierId, request));
    }

    @PostMapping("/{boxId}/tiers/{tierId}/transfer-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> transferInMore(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody TransferInMoreRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.transferInMore(boxId, tierId, request));
    }

    @PostMapping("/{boxId}/tiers/{tierId}/transfer-in-inventory-only")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> transferInInventoryOnly(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody TransferInMoreRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.transferInInventoryOnly(boxId, tierId, request));
    }

    @PostMapping("/{boxId}/tiers/{tierId}/add-slip")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> addSlip(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody AddSlipRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.addSlip(boxId, tierId, request));
    }

    // ===================== Draws =====================

    @PostMapping("/{boxId}/draws")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> recordDraw(
            @PathVariable UUID boxId,
            @Valid @RequestBody RecordDrawRequestDTO request) {
        return ResponseEntity.ok(kujiBoxService.recordDraw(boxId, request));
    }

    @PostMapping("/{boxId}/draws/{auditLogId}/undo")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> undoDraw(
            @PathVariable UUID boxId,
            @PathVariable UUID auditLogId,
            @RequestParam UUID actorId) {
        return ResponseEntity.ok(kujiBoxService.undoDraw(boxId, auditLogId, actorId));
    }
}
