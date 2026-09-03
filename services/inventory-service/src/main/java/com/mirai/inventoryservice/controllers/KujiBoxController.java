package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.kuji.AddKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.AddSlipRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.CloseKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.DeletePrizeRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.MoveSlipsRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.OpenKujiBoxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.PatchKujiTierRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.RecordDrawRequestDTO;
import com.mirai.inventoryservice.dtos.requests.kuji.TransferInMoreRequestDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiAllocationByLocationDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiAllocationByProductDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiBoxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.kuji.KujiDailyPayoutsResponseDTO;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.services.KujiBoxService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<KujiBoxResponseDTO> getActiveBoxByProduct(@PathVariable UUID productId, Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.getActiveBoxByProduct(productId);
        if (box == null) {
            return ResponseEntity.noContent().build();
        }
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @GetMapping("/by-product/{productId}/history")
    public ResponseEntity<List<KujiBoxResponseDTO>> getBoxHistory(@PathVariable UUID productId, Authentication authentication) {
        List<KujiBoxResponseDTO> boxes = kujiBoxService.getBoxHistory(productId);
        boxes.forEach(box -> applyPriceVisibility(box, authentication));
        return ResponseEntity.ok(boxes);
    }

    @GetMapping("/by-product/{productId}/last-tiers")
    public ResponseEntity<List<KujiBoxTierResponseDTO>> getLastClosedTiers(@PathVariable UUID productId, Authentication authentication) {
        List<KujiBoxTierResponseDTO> tiers = kujiBoxService.cloneTiersFromLastClosedBox(productId);
        tiers.forEach(tier -> applyPriceVisibility(tier, authentication));
        return ResponseEntity.ok(tiers);
    }

    @GetMapping("/{boxId}")
    public ResponseEntity<KujiBoxResponseDTO> getBox(@PathVariable UUID boxId, Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.getBox(boxId);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    /**
     * Per-day net payout series for a box. Defaults `from` to the box's opened-at date
     * (in {@code tz}) and `to` to today in {@code tz}. The returned series is always dense.
     */
    @GetMapping("/{boxId}/daily-payouts")
    public ResponseEntity<KujiDailyPayoutsResponseDTO> getDailyPayouts(
            @PathVariable UUID boxId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to,
            @RequestParam(required = false, defaultValue = "UTC") String tz,
            Authentication authentication
    ) {
        KujiDailyPayoutsResponseDTO payouts = kujiBoxService.getDailyPayouts(boxId, from, to, tz);
        return ResponseEntity.ok(applyPriceVisibility(payouts, authentication));
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
    public ResponseEntity<KujiBoxResponseDTO> openBox(@Valid @RequestBody OpenKujiBoxRequestDTO request, Authentication authentication) {
        KujiBoxResponseDTO created = kujiBoxService.openBox(request);
        applyPriceVisibility(created, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{boxId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> closeBox(
            @PathVariable UUID boxId,
            @Valid @RequestBody CloseKujiBoxRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.closeBox(boxId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PatchMapping("/{boxId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> reopenBox(
            @PathVariable UUID boxId,
            @RequestParam UUID actorId,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.reopenBox(boxId, actorId);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    // ===================== Tier mutations =====================

    @PostMapping("/{boxId}/tiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> addTier(
            @PathVariable UUID boxId,
            @Valid @RequestBody AddKujiTierRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.addTier(boxId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PatchMapping("/{boxId}/tiers/{tierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> patchTier(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody PatchKujiTierRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.patchTier(boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/transfer-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> transferInMore(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody TransferInMoreRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.transferInMore(boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/transfer-in-inventory-only")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> transferInInventoryOnly(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody TransferInMoreRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.transferInInventoryOnly(boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/add-slip")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> addSlip(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody AddSlipRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.addSlip(boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/move-slips")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> moveSlips(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody MoveSlipsRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.moveSlips(boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/delete-prize")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> deletePrize(
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody DeletePrizeRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.deletePrize(boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    // ===================== Draws =====================

    @PostMapping("/{boxId}/draws")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> recordDraw(
            @PathVariable UUID boxId,
            @Valid @RequestBody RecordDrawRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.recordDraw(boxId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/draws/{auditLogId}/undo")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> undoDraw(
            @PathVariable UUID boxId,
            @PathVariable UUID auditLogId,
            @RequestParam UUID actorId,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.undoDraw(boxId, auditLogId, actorId);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    /**
     * Null out tier price fields the caller's role isn't permitted to see - same gap and fix
     * pattern as ProductController.applyCostVisibility, for lib/rbac's canViewKujiPrices gate
     * (kuji-box-panel.tsx, kuji-history-detail-dialog.tsx), which previously hid the values in
     * the UI while the API returned them regardless.
     */
    private void applyPriceVisibility(KujiBoxResponseDTO box, Authentication authentication) {
        if (box == null || RolePermissions.hasPermission(authentication, Permission.KUJI_PRICES_VIEW)) {
            return;
        }
        if (box.getTiers() != null) {
            box.getTiers().forEach(this::stripTierPrices);
        }
    }

    private void applyPriceVisibility(KujiBoxTierResponseDTO tier, Authentication authentication) {
        if (RolePermissions.hasPermission(authentication, Permission.KUJI_PRICES_VIEW)) {
            return;
        }
        stripTierPrices(tier);
    }

    private void stripTierPrices(KujiBoxTierResponseDTO tier) {
        tier.setPrice(null);
        tier.setLinkedProductPrice(null);
    }

    /**
     * Same policy as the tier-price stripping above, applied to daily payout values - a
     * follow-up review found this endpoint (also EMPLOYEE-accessible) still returning
     * valueWon unconditionally. KujiDailyPayoutsResponseDTO is an immutable record, so
     * redaction means building a new instance rather than mutating in place.
     */
    private KujiDailyPayoutsResponseDTO applyPriceVisibility(KujiDailyPayoutsResponseDTO payouts, Authentication authentication) {
        if (payouts == null || RolePermissions.hasPermission(authentication, Permission.KUJI_PRICES_VIEW)) {
            return payouts;
        }
        List<KujiDailyPayoutsResponseDTO.DailyPoint> redactedSeries = payouts.series() == null ? null
                : payouts.series().stream()
                        .map(point -> new KujiDailyPayoutsResponseDTO.DailyPoint(point.date(), null, point.slipCount()))
                        .toList();
        KujiDailyPayoutsResponseDTO.Totals redactedTotal = payouts.total() == null ? null
                : new KujiDailyPayoutsResponseDTO.Totals(null, payouts.total().slipCount());
        return new KujiDailyPayoutsResponseDTO(
                payouts.boxId(), payouts.from(), payouts.to(), payouts.tz(), redactedSeries, redactedTotal);
    }
}
