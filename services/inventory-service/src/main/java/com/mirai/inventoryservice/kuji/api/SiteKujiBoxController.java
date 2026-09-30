package com.mirai.inventoryservice.kuji.api;

import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.kuji.application.KujiBoxService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Site-scoped Kuji box routes (.specs/phase-7-kuji-lootbox T-3, AC-5), reusing the generic
 * {@code /api/v1/sites/{siteId}/**} authorization filter -- {@code siteId} is still declared on
 * every handler so springdoc/the generated client expose it (matching
 * {@code shipments.api.SiteShipmentController}'s precedent), but the trusted value always comes
 * from {@link AuthorizedSiteContextHolder}. Every {@link KujiBoxService} method this delegates to
 * was already made site-parameterized in T-3; this controller adds no new business logic, only a
 * v1 route shape mirroring {@link KujiBoxController}'s legacy one route-for-route. Distinct
 * handler names from {@link KujiBoxController}'s so springdoc doesn't collide operation IDs.
 * <p>
 * Unlike the legacy route's {@code reopenBox}/{@code undoDraw}, the actor for a v1 mutation is
 * always the authenticated principal from the resolved site context, never a client-supplied
 * {@code actorId} query parameter -- matching {@code SiteMachineDisplayController}'s actor-
 * spoofing fix.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/kuji-boxes")
public class SiteKujiBoxController {

    private final KujiBoxService kujiBoxService;

    public SiteKujiBoxController(KujiBoxService kujiBoxService) {
        this.kujiBoxService = kujiBoxService;
    }

    private UUID contextSiteId() {
        return AuthorizedSiteContextHolder.require().siteId();
    }

    private UUID contextActorId() {
        return AuthorizedSiteContextHolder.require().backendUserId();
    }

    // ===================== Reads =====================

    @GetMapping("/by-product/{productId}/active")
    public ResponseEntity<KujiBoxResponseDTO> getSiteActiveBoxByProduct(
            @PathVariable UUID siteId, @PathVariable UUID productId, Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.getActiveBoxByProduct(contextSiteId(), productId);
        if (box == null) {
            return ResponseEntity.noContent().build();
        }
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @GetMapping("/by-product/{productId}/history")
    public ResponseEntity<List<KujiBoxResponseDTO>> getSiteBoxHistory(
            @PathVariable UUID siteId, @PathVariable UUID productId, Authentication authentication) {
        List<KujiBoxResponseDTO> boxes = kujiBoxService.getBoxHistory(contextSiteId(), productId);
        boxes.forEach(box -> applyPriceVisibility(box, authentication));
        return ResponseEntity.ok(boxes);
    }

    @GetMapping("/by-product/{productId}/last-tiers")
    public ResponseEntity<List<KujiBoxTierResponseDTO>> getSiteLastClosedTiers(
            @PathVariable UUID siteId, @PathVariable UUID productId, Authentication authentication) {
        List<KujiBoxTierResponseDTO> tiers = kujiBoxService.cloneTiersFromLastClosedBox(contextSiteId(), productId);
        tiers.forEach(tier -> applyPriceVisibility(tier, authentication));
        return ResponseEntity.ok(tiers);
    }

    @GetMapping("/{boxId}")
    public ResponseEntity<KujiBoxResponseDTO> getSiteBox(
            @PathVariable UUID siteId, @PathVariable UUID boxId, Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.getBox(contextSiteId(), boxId);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @GetMapping("/{boxId}/daily-payouts")
    public ResponseEntity<KujiDailyPayoutsResponseDTO> getSiteDailyPayouts(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false, defaultValue = "UTC") String tz,
            Authentication authentication) {
        KujiDailyPayoutsResponseDTO payouts = kujiBoxService.getDailyPayouts(contextSiteId(), boxId, from, to, tz);
        return ResponseEntity.ok(applyPriceVisibility(payouts, authentication));
    }

    @GetMapping("/allocations/by-location/{locationId}")
    public ResponseEntity<List<KujiAllocationByLocationDTO>> getSiteAllocationsByLocation(
            @PathVariable UUID siteId, @PathVariable UUID locationId) {
        return ResponseEntity.ok(kujiBoxService.getAllocationsByLocation(contextSiteId(), locationId));
    }

    @GetMapping("/allocations/by-product/{productId}")
    public ResponseEntity<List<KujiAllocationByProductDTO>> getSiteAllocationsByProduct(
            @PathVariable UUID siteId, @PathVariable UUID productId) {
        return ResponseEntity.ok(kujiBoxService.getAllocationsByProduct(contextSiteId(), productId));
    }

    // ===================== Lifecycle =====================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> openSiteBox(
            @PathVariable UUID siteId,
            @Valid @RequestBody OpenKujiBoxRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO created = kujiBoxService.openBox(contextSiteId(), request);
        applyPriceVisibility(created, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{boxId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> closeSiteBox(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @Valid @RequestBody CloseKujiBoxRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.closeBox(contextSiteId(), boxId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PatchMapping("/{boxId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> reopenSiteBox(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.reopenBox(contextSiteId(), boxId, contextActorId());
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    // ===================== Tier mutations =====================

    @PostMapping("/{boxId}/tiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> addSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @Valid @RequestBody AddKujiTierRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.addTier(contextSiteId(), boxId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PatchMapping("/{boxId}/tiers/{tierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> patchSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody PatchKujiTierRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.patchTier(contextSiteId(), boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/transfer-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> transferInMoreToSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody TransferInMoreRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.transferInMore(contextSiteId(), boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/transfer-in-inventory-only")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<KujiBoxResponseDTO> transferInInventoryOnlyToSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody TransferInMoreRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.transferInInventoryOnly(contextSiteId(), boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/add-slip")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> addSlipToSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody AddSlipRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.addSlip(contextSiteId(), boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/move-slips")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> moveSlipsInSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody MoveSlipsRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.moveSlips(contextSiteId(), boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/tiers/{tierId}/delete-prize")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> deleteSitePrize(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID tierId,
            @Valid @RequestBody DeletePrizeRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.deletePrize(contextSiteId(), boxId, tierId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    // ===================== Draws =====================

    @PostMapping("/{boxId}/draws")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> recordSiteDraw(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @Valid @RequestBody RecordDrawRequestDTO request,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.recordDraw(contextSiteId(), boxId, request);
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    @PostMapping("/{boxId}/draws/{auditLogId}/undo")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<KujiBoxResponseDTO> undoSiteDraw(
            @PathVariable UUID siteId,
            @PathVariable UUID boxId,
            @PathVariable UUID auditLogId,
            Authentication authentication) {
        KujiBoxResponseDTO box = kujiBoxService.undoDraw(contextSiteId(), boxId, auditLogId, contextActorId());
        applyPriceVisibility(box, authentication);
        return ResponseEntity.ok(box);
    }

    /**
     * Same price-visibility policy as {@link KujiBoxController} -- duplicated rather than shared
     * because it is a per-request, per-controller HTTP-layer concern, matching how each of that
     * controller's route siblings in this codebase owns its own visibility stripping.
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
