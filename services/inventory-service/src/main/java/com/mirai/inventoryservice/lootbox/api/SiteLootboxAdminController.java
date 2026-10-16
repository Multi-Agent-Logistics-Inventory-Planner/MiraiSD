package com.mirai.inventoryservice.lootbox.api;

import com.mirai.inventoryservice.dtos.responses.AdminCoinActivityDTO;
import com.mirai.inventoryservice.dtos.responses.CoinAdjustmentResponseDTO;
import com.mirai.inventoryservice.dtos.responses.CoinStatsResponseDTO;
import com.mirai.inventoryservice.dtos.responses.PlayerCoinRowDTO;
import com.mirai.inventoryservice.dtos.responses.UserCoinProfileResponseDTO;
import com.mirai.inventoryservice.lootbox.application.CoinAdminDashboardService;
import com.mirai.inventoryservice.lootbox.application.LootboxAdminService;
import com.mirai.inventoryservice.lootbox.application.LootboxService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Site-scoped lootbox admin routes (.specs/phase-7-kuji-lootbox T-3, AC-5), reusing the generic
 * {@code /api/v1/sites/{siteId}/**} authorization filter -- {@code siteId} is still declared on
 * every handler so springdoc/the generated client expose it (matching
 * {@code shipments.api.SiteShipmentController}'s precedent), but the trusted value always comes
 * from {@link AuthorizedSiteContextHolder}. Every service method this delegates to was already
 * made site-parameterized during Lootbox site-scoping; this controller adds no new business
 * logic, only a v1 route shape mirroring {@link LootboxAdminController}'s legacy one
 * route-for-route (minus coin-config, see below). Distinct handler names from
 * {@link LootboxAdminController}'s so springdoc doesn't collide operation IDs.
 * <p>
 * {@code coin-config} is deliberately NOT mirrored here: per the T-2 site-ownership decision,
 * {@code coin_economy_config} stays a single global/org-wide row, not a per-site resource, so
 * {@link LootboxAdminController#getCoinConfig()}/{@code updateCoinConfig} have no site-scoped
 * equivalent to migrate to -- exposing them under a {@code /sites/{siteId}/} path would falsely
 * imply the rate is per-site. They remain reachable only through the legacy
 * {@code /api/lootbox/admin/coin-config} route.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/lootbox/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SiteLootboxAdminController {

    private final LootboxAdminService lootboxAdminService;
    private final LootboxService lootboxService;
    private final CoinAdminDashboardService coinAdminDashboardService;

    private UUID contextSiteId() {
        return AuthorizedSiteContextHolder.require().siteId();
    }

    private UUID contextAdminId() {
        return AuthorizedSiteContextHolder.require().backendUserId();
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxResponseDTO>> getSiteFullCatalog(@PathVariable UUID siteId) {
        return ResponseEntity.ok(lootboxService.getAdminCatalog(contextSiteId()));
    }

    // ----- Crate CRUD -----

    @GetMapping("/crates")
    public ResponseEntity<List<LootboxAdminResponseDTO>> listSiteCrates(@PathVariable UUID siteId) {
        return ResponseEntity.ok(lootboxAdminService.listCrates(contextSiteId()));
    }

    @PostMapping("/crates")
    public ResponseEntity<LootboxAdminResponseDTO> createSiteCrate(
            @PathVariable UUID siteId, @Valid @RequestBody UpsertLootboxRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createCrate(contextSiteId(), req));
    }

    @PatchMapping("/crates/{id}")
    public ResponseEntity<LootboxAdminResponseDTO> updateSiteCrate(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @RequestBody UpsertLootboxRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updateCrate(contextSiteId(), id, req));
    }

    @DeleteMapping("/crates/{id}")
    public ResponseEntity<Void> deleteSiteCrate(@PathVariable UUID siteId, @PathVariable UUID id) {
        lootboxAdminService.deleteCrate(contextSiteId(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- Tier CRUD -----

    @PostMapping("/tiers")
    public ResponseEntity<LootboxTierResponseDTO> createSiteTier(
            @PathVariable UUID siteId, @Valid @RequestBody UpsertTierRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createTier(contextSiteId(), req));
    }

    @PatchMapping("/tiers/{id}")
    public ResponseEntity<LootboxTierResponseDTO> updateSiteTier(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @RequestBody UpsertTierRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updateTier(contextSiteId(), id, req));
    }

    @DeleteMapping("/tiers/{id}")
    public ResponseEntity<Void> deleteSiteTier(@PathVariable UUID siteId, @PathVariable UUID id) {
        lootboxAdminService.deleteTier(contextSiteId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tiers/bulk-update")
    public ResponseEntity<List<LootboxTierResponseDTO>> bulkUpdateSiteTierProbabilities(
            @PathVariable UUID siteId, @Valid @RequestBody BulkUpdateTierProbabilitiesRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.bulkUpdateTierProbabilities(contextSiteId(), req));
    }

    // ----- Prize CRUD -----

    @PostMapping("/prizes")
    public ResponseEntity<LootboxPrizeResponseDTO> createSitePrize(
            @PathVariable UUID siteId, @Valid @RequestBody UpsertPrizeRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createPrize(contextSiteId(), req));
    }

    @PatchMapping("/prizes/{id}")
    public ResponseEntity<LootboxPrizeResponseDTO> updateSitePrize(
            @PathVariable UUID siteId,
            @PathVariable UUID id,
            @RequestBody UpsertPrizeRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updatePrize(contextSiteId(), id, req));
    }

    @DeleteMapping("/prizes/{id}")
    public ResponseEntity<Void> deleteSitePrize(@PathVariable UUID siteId, @PathVariable UUID id) {
        lootboxAdminService.deletePrize(contextSiteId(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- Redemption -----

    @GetMapping("/pending")
    public ResponseEntity<Page<LootboxPlayResponseDTO>> getSitePending(
            @PathVariable UUID siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "WON") String status) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(lootboxAdminService.getPlaysByStatus(contextSiteId(), status, pageable));
    }

    @PostMapping("/redeem/{playId}")
    public ResponseEntity<LootboxPlayResponseDTO> markSiteRedeemed(
            @PathVariable UUID siteId, @PathVariable UUID playId) {
        return ResponseEntity.ok(lootboxAdminService.markRedeemed(contextSiteId(), playId, contextAdminId()));
    }

    // ----- Adjustments -----

    @PostMapping("/adjustments")
    public ResponseEntity<CoinAdjustmentResponseDTO> createSiteAdjustment(
            @PathVariable UUID siteId, @Valid @RequestBody CoinAdjustmentRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createAdjustment(contextSiteId(), req, contextAdminId()));
    }

    // ----- User profile -----

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserCoinProfileResponseDTO> getSiteUserProfile(
            @PathVariable UUID siteId, @PathVariable UUID userId) {
        return ResponseEntity.ok(lootboxAdminService.getUserProfile(contextSiteId(), userId));
    }

    // ----- Coins-tab dashboard -----

    @GetMapping("/coin-stats")
    public ResponseEntity<CoinStatsResponseDTO> getSiteCoinStats(@PathVariable UUID siteId) {
        return ResponseEntity.ok(coinAdminDashboardService.getStats(contextSiteId()));
    }

    @GetMapping("/players")
    public ResponseEntity<List<PlayerCoinRowDTO>> getSitePlayers(
            @PathVariable UUID siteId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return ResponseEntity.ok(coinAdminDashboardService.getPlayers(contextSiteId(), search, limit, offset));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<AdminCoinActivityDTO>> getSiteActivity(
            @PathVariable UUID siteId, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(coinAdminDashboardService.getRecentActivity(contextSiteId(), limit));
    }
}
