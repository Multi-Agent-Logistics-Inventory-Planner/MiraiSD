package com.mirai.inventoryservice.lootbox.api;

import com.mirai.inventoryservice.lootbox.api.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.lootbox.api.CoinAdjustmentRequestDTO;
import com.mirai.inventoryservice.lootbox.api.UpdateCoinEconomyConfigRequestDTO;
import com.mirai.inventoryservice.lootbox.api.UpsertLootboxRequestDTO;
import com.mirai.inventoryservice.lootbox.api.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.lootbox.api.UpsertTierRequestDTO;
import com.mirai.inventoryservice.dtos.responses.AdminCoinActivityDTO;
import com.mirai.inventoryservice.dtos.responses.CoinAdjustmentResponseDTO;
import com.mirai.inventoryservice.lootbox.api.CoinEconomyConfigResponseDTO;
import com.mirai.inventoryservice.dtos.responses.CoinStatsResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxAdminResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.PlayerCoinRowDTO;
import com.mirai.inventoryservice.dtos.responses.UserCoinProfileResponseDTO;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.UserNotFoundException;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.lootbox.domain.CoinEconomyConfig;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.lootbox.application.CoinAdminDashboardService;
import com.mirai.inventoryservice.lootbox.application.CoinEconomyService;
import com.mirai.inventoryservice.lootbox.application.LootboxAdminService;
import com.mirai.inventoryservice.lootbox.application.LootboxService;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/lootbox/admin")
@PreAuthorize("@authGate.isDevMode() or hasRole('ADMIN')")
@RequiredArgsConstructor
public class LootboxAdminController {

    private static final String COIN_RATE_HINT =
            "Changes take effect at the next 6 AM ET review fetch. Existing balances are unchanged.";

    private final LootboxAdminService lootboxAdminService;
    private final LootboxService lootboxService;
    private final CoinEconomyService coinEconomyService;
    private final CoinAdminDashboardService coinAdminDashboardService;
    private final UserRepository userRepository;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;
    private final SiteDirectory siteDirectory;

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxResponseDTO>> getFullCatalog(Authentication auth) {
        return ResponseEntity.ok(lootboxService.getAdminCatalog(resolveSiteId(auth)));
    }

    // ----- Crate CRUD -----

    @GetMapping("/crates")
    public ResponseEntity<List<LootboxAdminResponseDTO>> listCrates(Authentication auth) {
        return ResponseEntity.ok(lootboxAdminService.listCrates(resolveSiteId(auth)));
    }

    @PostMapping("/crates")
    public ResponseEntity<LootboxAdminResponseDTO> createCrate(
            Authentication auth, @Valid @RequestBody UpsertLootboxRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createCrate(resolveSiteId(auth), req));
    }

    @PatchMapping("/crates/{id}")
    public ResponseEntity<LootboxAdminResponseDTO> updateCrate(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody UpsertLootboxRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updateCrate(resolveSiteId(auth), id, req));
    }

    @DeleteMapping("/crates/{id}")
    public ResponseEntity<Void> deleteCrate(Authentication auth, @PathVariable UUID id) {
        lootboxAdminService.deleteCrate(resolveSiteId(auth), id);
        return ResponseEntity.noContent().build();
    }

    // ----- Tier CRUD -----

    @PostMapping("/tiers")
    public ResponseEntity<LootboxTierResponseDTO> createTier(
            Authentication auth, @Valid @RequestBody UpsertTierRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createTier(resolveSiteId(auth), req));
    }

    @PatchMapping("/tiers/{id}")
    public ResponseEntity<LootboxTierResponseDTO> updateTier(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody UpsertTierRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updateTier(resolveSiteId(auth), id, req));
    }

    @DeleteMapping("/tiers/{id}")
    public ResponseEntity<Void> deleteTier(Authentication auth, @PathVariable UUID id) {
        lootboxAdminService.deleteTier(resolveSiteId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tiers/bulk-update")
    public ResponseEntity<List<LootboxTierResponseDTO>> bulkUpdateTierProbabilities(
            Authentication auth, @Valid @RequestBody BulkUpdateTierProbabilitiesRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.bulkUpdateTierProbabilities(resolveSiteId(auth), req));
    }

    // ----- Prize CRUD -----

    @PostMapping("/prizes")
    public ResponseEntity<LootboxPrizeResponseDTO> createPrize(
            Authentication auth, @Valid @RequestBody UpsertPrizeRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createPrize(resolveSiteId(auth), req));
    }

    @PatchMapping("/prizes/{id}")
    public ResponseEntity<LootboxPrizeResponseDTO> updatePrize(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody UpsertPrizeRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updatePrize(resolveSiteId(auth), id, req));
    }

    @DeleteMapping("/prizes/{id}")
    public ResponseEntity<Void> deletePrize(Authentication auth, @PathVariable UUID id) {
        lootboxAdminService.deletePrize(resolveSiteId(auth), id);
        return ResponseEntity.noContent().build();
    }

    // ----- Redemption -----

    @GetMapping("/pending")
    public ResponseEntity<Page<LootboxPlayResponseDTO>> getPending(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "WON") String status) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(lootboxAdminService.getPlaysByStatus(resolveSiteId(auth), status, pageable));
    }

    @PostMapping("/redeem/{playId}")
    public ResponseEntity<LootboxPlayResponseDTO> markRedeemed(
            @PathVariable UUID playId,
            Authentication auth) {
        UUID siteId = resolveSiteId(auth);
        UUID adminId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxAdminService.markRedeemed(siteId, playId, adminId));
    }

    // ----- Adjustments -----

    @PostMapping("/adjustments")
    public ResponseEntity<CoinAdjustmentResponseDTO> createAdjustment(
            @Valid @RequestBody CoinAdjustmentRequestDTO req,
            Authentication auth) {
        UUID siteId = resolveSiteId(auth);
        UUID adminId = resolveUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createAdjustment(siteId, req, adminId));
    }

    // ----- Coin economy config -----

    @GetMapping("/coin-config")
    public ResponseEntity<CoinEconomyConfigResponseDTO> getCoinConfig() {
        return ResponseEntity.ok(toConfigDto(coinEconomyService.getConfig()));
    }

    @PutMapping("/coin-config")
    public ResponseEntity<CoinEconomyConfigResponseDTO> updateCoinConfig(
            @Valid @RequestBody UpdateCoinEconomyConfigRequestDTO req,
            Authentication auth) {
        UUID adminId = resolveUserId(auth);
        CoinEconomyConfig updated = coinEconomyService.setReviewRate(req.reviewCoinRate(), adminId);
        return ResponseEntity.ok(toConfigDto(updated));
    }

    private CoinEconomyConfigResponseDTO toConfigDto(CoinEconomyConfig config) {
        User updatedBy = config.getUpdatedBy();
        return CoinEconomyConfigResponseDTO.builder()
                .reviewCoinRate(config.getReviewCoinRate())
                .updatedAt(config.getUpdatedAt())
                .updatedByUserId(updatedBy != null ? updatedBy.getId() : null)
                .updatedByName(updatedBy != null ? updatedBy.getFullName() : null)
                .nextFetchHint(COIN_RATE_HINT)
                .build();
    }

    // ----- User profile -----

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserCoinProfileResponseDTO> getUserProfile(
            Authentication auth, @PathVariable UUID userId) {
        return ResponseEntity.ok(lootboxAdminService.getUserProfile(resolveSiteId(auth), userId));
    }

    // ----- Coins-tab dashboard -----

    @GetMapping("/coin-stats")
    public ResponseEntity<CoinStatsResponseDTO> getCoinStats(Authentication auth) {
        return ResponseEntity.ok(coinAdminDashboardService.getStats(resolveSiteId(auth)));
    }

    @GetMapping("/players")
    public ResponseEntity<List<PlayerCoinRowDTO>> getPlayers(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return ResponseEntity.ok(coinAdminDashboardService.getPlayers(resolveSiteId(auth), search, limit, offset));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<AdminCoinActivityDTO>> getActivity(
            Authentication auth, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(coinAdminDashboardService.getRecentActivity(resolveSiteId(auth), limit));
    }

    /**
     * Legacy unversioned routes mean MAIN (.specs/phase-7-kuji-lootbox T-3, matching
     * shipments/displays precedent). Dev-mode requests with no principal skip the membership
     * check entirely and resolve MAIN directly instead of via LegacyMainSiteContextResolver,
     * which requires a real authenticated principal.
     */
    private UUID resolveSiteId(Authentication auth) {
        if (extractBackendUserId(auth) == null) {
            return siteDirectory.findByCode(LocationService.DEFAULT_SITE_CODE)
                    .orElseThrow(() -> new IllegalStateException("MAIN site not found"))
                    .id();
        }
        return legacyMainSiteContextResolver.requireMain().siteId();
    }

    private UUID resolveUserId(Authentication auth) {
        UUID backendUserId = extractBackendUserId(auth);
        if (backendUserId == null) {
            // Dev fallback (see LootboxController.resolveUserId).
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .findFirst()
                    .map(User::getId)
                    .orElseThrow(() -> new UserNotFoundException(
                            "No authenticated user and no ADMIN user in DB to fall back to."));
        }
        return backendUserId;
    }

    private static UUID extractBackendUserId(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            return authenticatedPrincipal.backendUserId();
        }
        return null;
    }
}
