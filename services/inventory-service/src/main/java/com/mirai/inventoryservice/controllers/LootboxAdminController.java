package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.lootbox.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.CoinAdjustmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertTierRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CoinAdjustmentResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserCoinProfileResponseDTO;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.UserRepository;
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

    private final LootboxAdminService lootboxAdminService;
    private final LootboxService lootboxService;
    private final UserRepository userRepository;

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxTierResponseDTO>> getFullCatalog() {
        return ResponseEntity.ok(lootboxService.getCatalog(false));
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
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(lootboxAdminService.getPendingRedemptions(pageable));
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

    // ----- User profile -----

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserCoinProfileResponseDTO> getUserProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(lootboxAdminService.getUserProfile(userId));
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
