package com.mirai.inventoryservice.lootbox.api;

import com.mirai.inventoryservice.lootbox.api.PlayLootboxRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CoinHistoryEntryDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxBalanceResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxResponseDTO;
import com.mirai.inventoryservice.lootbox.api.PlayLootboxResponseDTO;
import com.mirai.inventoryservice.lootbox.api.RecentLootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.WalletBreakdownResponseDTO;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.UserNotFoundException;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.services.IdempotencyService;
import com.mirai.inventoryservice.lootbox.application.LootboxService;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/lootbox")
@PreAuthorize("@authGate.isDevMode() or hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
@RequiredArgsConstructor
public class LootboxController {

    private final LootboxService lootboxService;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;
    private final SiteDirectory siteDirectory;

    @GetMapping("/balance")
    public ResponseEntity<LootboxBalanceResponseDTO> getBalance(Authentication auth) {
        UUID siteId = resolveSiteId(auth);
        UUID userId = resolveUserId(auth);
        LootboxService.BalanceBreakdown bb = lootboxService.computeBalance(siteId, userId);
        return ResponseEntity.ok(LootboxBalanceResponseDTO.builder()
                .balance(bb.balance())
                .reviewCredits(bb.reviewCredits())
                .totalAdjustments(bb.totalAdjustments())
                .totalSpent(bb.totalSpent())
                .totalExpired(bb.totalExpired())
                .build());
    }

    @GetMapping("/wallet/breakdown")
    public ResponseEntity<WalletBreakdownResponseDTO> getWalletBreakdown(Authentication auth) {
        UUID siteId = resolveSiteId(auth);
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxService.getWalletBreakdown(siteId, userId));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxResponseDTO>> getCatalog(Authentication auth) {
        return ResponseEntity.ok(lootboxService.getCatalog(resolveSiteId(auth)));
    }

    @PostMapping("/play")
    public ResponseEntity<PlayLootboxResponseDTO> play(
            Authentication auth,
            @Valid @RequestBody PlayLootboxRequestDTO body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID siteId = resolveSiteId(auth);
        UUID userId = resolveUserId(auth);

        var cached = idempotencyService.get(userId, idempotencyKey, PlayLootboxResponseDTO.class);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        LootboxService.PlayResult result = lootboxService.play(siteId, userId, body.crateId(), idempotencyKey);
        PlayLootboxResponseDTO response = PlayLootboxResponseDTO.builder()
                .play(LootboxService.toPlayDto(result.play()))
                .newBalance(result.newBalance())
                .build();
        idempotencyService.put(userId, idempotencyKey, response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-prizes")
    public ResponseEntity<List<LootboxPlayResponseDTO>> getMyPrizes(Authentication auth) {
        UUID siteId = resolveSiteId(auth);
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxService.getUserPrizes(siteId, userId));
    }

    @GetMapping("/my-history")
    public ResponseEntity<List<CoinHistoryEntryDTO>> getMyHistory(Authentication auth) {
        UUID siteId = resolveSiteId(auth);
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxService.getUserHistory(siteId, userId));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<RecentLootboxPlayResponseDTO>> getRecentPlays(
            Authentication auth,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID crateId) {
        return ResponseEntity.ok(lootboxService.listRecentPlays(resolveSiteId(auth), limit, crateId));
    }

    private UUID resolveUserId(Authentication auth) {
        UUID backendUserId = extractBackendUserId(auth);
        if (backendUserId == null) {
            // Dev fallback: when path-level auth is disabled there's no principal to map.
            // Use the first ADMIN user as the actor so the feature can be exercised E2E.
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .findFirst()
                    .map(User::getId)
                    .orElseThrow(() -> new UserNotFoundException(
                            "No authenticated user and no ADMIN user in DB to fall back to."));
        }
        return backendUserId;
    }

    /**
     * Legacy unversioned routes mean MAIN (.specs/phase-7-kuji-lootbox T-3, matching
     * shipments/displays precedent). Dev-mode requests with no principal (see
     * resolveUserId's fallback) skip the membership check entirely -- there is no caller to
     * authorize against -- and resolve MAIN directly instead of via
     * LegacyMainSiteContextResolver, which requires a real authenticated principal.
     */
    private UUID resolveSiteId(Authentication auth) {
        if (extractBackendUserId(auth) == null) {
            return siteDirectory.findByCode(LocationService.DEFAULT_SITE_CODE)
                    .orElseThrow(() -> new IllegalStateException("MAIN site not found"))
                    .id();
        }
        return legacyMainSiteContextResolver.requireMain().siteId();
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
