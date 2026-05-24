package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.lootbox.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.CoinAdjustmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpdateCoinEconomyConfigRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertLootboxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertTierRequestDTO;
import com.mirai.inventoryservice.dtos.responses.AdminCoinActivityDTO;
import com.mirai.inventoryservice.dtos.responses.CoinAdjustmentResponseDTO;
import com.mirai.inventoryservice.dtos.responses.CoinEconomyConfigResponseDTO;
import com.mirai.inventoryservice.dtos.responses.CoinStatsResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxAdminResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.PlayerCoinRowDTO;
import com.mirai.inventoryservice.dtos.responses.UserCoinProfileResponseDTO;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.models.lootbox.CoinEconomyConfig;
import com.mirai.inventoryservice.repositories.UserRepository;
import com.mirai.inventoryservice.services.CoinAdminDashboardService;
import com.mirai.inventoryservice.services.CoinEconomyService;
import com.mirai.inventoryservice.services.LootboxAdminService;
import com.mirai.inventoryservice.services.LootboxService;
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
import java.util.Map;
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

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxResponseDTO>> getFullCatalog() {
        return ResponseEntity.ok(lootboxService.getAdminCatalog());
    }

    // ----- Crate CRUD -----

    @GetMapping("/crates")
    public ResponseEntity<List<LootboxAdminResponseDTO>> listCrates() {
        return ResponseEntity.ok(lootboxAdminService.listCrates());
    }

    @PostMapping("/crates")
    public ResponseEntity<LootboxAdminResponseDTO> createCrate(@Valid @RequestBody UpsertLootboxRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lootboxAdminService.createCrate(req));
    }

    @PatchMapping("/crates/{id}")
    public ResponseEntity<LootboxAdminResponseDTO> updateCrate(
            @PathVariable UUID id,
            @RequestBody UpsertLootboxRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updateCrate(id, req));
    }

    @DeleteMapping("/crates/{id}")
    public ResponseEntity<Void> deleteCrate(@PathVariable UUID id) {
        lootboxAdminService.deleteCrate(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Tier CRUD -----

    @PostMapping("/tiers")
    public ResponseEntity<LootboxTierResponseDTO> createTier(@Valid @RequestBody UpsertTierRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lootboxAdminService.createTier(req));
    }

    @PatchMapping("/tiers/{id}")
    public ResponseEntity<LootboxTierResponseDTO> updateTier(
            @PathVariable UUID id,
            @RequestBody UpsertTierRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updateTier(id, req));
    }

    @DeleteMapping("/tiers/{id}")
    public ResponseEntity<Void> deleteTier(@PathVariable UUID id) {
        lootboxAdminService.deleteTier(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tiers/bulk-update")
    public ResponseEntity<List<LootboxTierResponseDTO>> bulkUpdateTierProbabilities(
            @Valid @RequestBody BulkUpdateTierProbabilitiesRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.bulkUpdateTierProbabilities(req));
    }

    // ----- Prize CRUD -----

    @PostMapping("/prizes")
    public ResponseEntity<LootboxPrizeResponseDTO> createPrize(@Valid @RequestBody UpsertPrizeRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lootboxAdminService.createPrize(req));
    }

    @PatchMapping("/prizes/{id}")
    public ResponseEntity<LootboxPrizeResponseDTO> updatePrize(
            @PathVariable UUID id,
            @RequestBody UpsertPrizeRequestDTO req) {
        return ResponseEntity.ok(lootboxAdminService.updatePrize(id, req));
    }

    @DeleteMapping("/prizes/{id}")
    public ResponseEntity<Void> deletePrize(@PathVariable UUID id) {
        lootboxAdminService.deletePrize(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Redemption -----

    @GetMapping("/pending")
    public ResponseEntity<Page<LootboxPlayResponseDTO>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "WON") String status) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(lootboxAdminService.getPlaysByStatus(status, pageable));
    }

    @PostMapping("/redeem/{playId}")
    public ResponseEntity<LootboxPlayResponseDTO> markRedeemed(
            @PathVariable UUID playId,
            Authentication auth) {
        UUID adminId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxAdminService.markRedeemed(playId, adminId));
    }

    // ----- Adjustments -----

    @PostMapping("/adjustments")
    public ResponseEntity<CoinAdjustmentResponseDTO> createAdjustment(
            @Valid @RequestBody CoinAdjustmentRequestDTO req,
            Authentication auth) {
        UUID adminId = resolveUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lootboxAdminService.createAdjustment(req, adminId));
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
    public ResponseEntity<UserCoinProfileResponseDTO> getUserProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(lootboxAdminService.getUserProfile(userId));
    }

    // ----- Coins-tab dashboard -----

    @GetMapping("/coin-stats")
    public ResponseEntity<CoinStatsResponseDTO> getCoinStats() {
        return ResponseEntity.ok(coinAdminDashboardService.getStats());
    }

    @GetMapping("/players")
    public ResponseEntity<List<PlayerCoinRowDTO>> getPlayers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return ResponseEntity.ok(coinAdminDashboardService.getPlayers(search, limit, offset));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<AdminCoinActivityDTO>> getActivity(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(coinAdminDashboardService.getRecentActivity(limit));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = extractEmail(auth);
        if (email == null) {
            // Dev fallback (see LootboxController.resolveUserId).
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .findFirst()
                    .map(User::getId)
                    .orElseThrow(() -> new UserNotFoundException(
                            "No authenticated user and no ADMIN user in DB to fall back to."));
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found: " + email));
        return user.getId();
    }

    @SuppressWarnings("unchecked")
    private static String extractEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Map<?, ?> map) {
            Object email = map.get("email");
            return email instanceof String s ? s : null;
        }
        return null;
    }
}
