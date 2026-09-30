package com.mirai.inventoryservice.lootbox.api;

import com.mirai.inventoryservice.dtos.responses.CoinHistoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.WalletBreakdownResponseDTO;
import com.mirai.inventoryservice.lootbox.application.LootboxService;
import com.mirai.inventoryservice.services.IdempotencyService;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Site-scoped lootbox routes (.specs/phase-7-kuji-lootbox T-3, AC-5), reusing the generic
 * {@code /api/v1/sites/{siteId}/**} authorization filter -- {@code siteId} is still declared on
 * every handler so springdoc/the generated client expose it (matching
 * {@code shipments.api.SiteShipmentController}'s precedent), but the trusted value always comes
 * from {@link AuthorizedSiteContextHolder}. Every {@link LootboxService} method this delegates to
 * was already made site-parameterized during Lootbox site-scoping; this controller adds no new
 * business logic, only a v1 route shape mirroring {@link LootboxController}'s legacy one
 * route-for-route. Distinct handler names from {@link LootboxController}'s so springdoc doesn't
 * collide operation IDs.
 * <p>
 * Unlike the legacy route, the actor is always the authenticated principal from the resolved site
 * context -- there is no dev-mode ADMIN fallback here, since the site-access filter this route
 * shares with every other v1 route already requires a real authenticated principal before a
 * handler is ever reached.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/lootbox")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
@RequiredArgsConstructor
public class SiteLootboxController {

    private final LootboxService lootboxService;
    private final IdempotencyService idempotencyService;

    private UUID contextSiteId() {
        return AuthorizedSiteContextHolder.require().siteId();
    }

    private UUID contextUserId() {
        return AuthorizedSiteContextHolder.require().backendUserId();
    }

    @GetMapping("/balance")
    public ResponseEntity<LootboxBalanceResponseDTO> getSiteBalance(@PathVariable UUID siteId) {
        UUID contextSiteId = contextSiteId();
        UUID userId = contextUserId();
        LootboxService.BalanceBreakdown bb = lootboxService.computeBalance(contextSiteId, userId);
        return ResponseEntity.ok(LootboxBalanceResponseDTO.builder()
                .balance(bb.balance())
                .reviewCredits(bb.reviewCredits())
                .totalAdjustments(bb.totalAdjustments())
                .totalSpent(bb.totalSpent())
                .totalExpired(bb.totalExpired())
                .build());
    }

    @GetMapping("/wallet/breakdown")
    public ResponseEntity<WalletBreakdownResponseDTO> getSiteWalletBreakdown(@PathVariable UUID siteId) {
        return ResponseEntity.ok(lootboxService.getWalletBreakdown(contextSiteId(), contextUserId()));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxResponseDTO>> getSiteCatalog(@PathVariable UUID siteId) {
        return ResponseEntity.ok(lootboxService.getCatalog(contextSiteId()));
    }

    @PostMapping("/play")
    public ResponseEntity<PlayLootboxResponseDTO> playSiteLootbox(
            @PathVariable UUID siteId,
            @Valid @RequestBody PlayLootboxRequestDTO body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID contextSiteId = contextSiteId();
        UUID userId = contextUserId();

        var cached = idempotencyService.get(userId, idempotencyKey, PlayLootboxResponseDTO.class);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        LootboxService.PlayResult result = lootboxService.play(contextSiteId, userId, body.crateId(), idempotencyKey);
        PlayLootboxResponseDTO response = PlayLootboxResponseDTO.builder()
                .play(LootboxService.toPlayDto(result.play()))
                .newBalance(result.newBalance())
                .build();
        idempotencyService.put(userId, idempotencyKey, response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-prizes")
    public ResponseEntity<List<LootboxPlayResponseDTO>> getMySitePrizes(@PathVariable UUID siteId) {
        return ResponseEntity.ok(lootboxService.getUserPrizes(contextSiteId(), contextUserId()));
    }

    @GetMapping("/my-history")
    public ResponseEntity<List<CoinHistoryEntryDTO>> getMySiteHistory(@PathVariable UUID siteId) {
        return ResponseEntity.ok(lootboxService.getUserHistory(contextSiteId(), contextUserId()));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<RecentLootboxPlayResponseDTO>> getRecentSitePlays(
            @PathVariable UUID siteId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID crateId) {
        return ResponseEntity.ok(lootboxService.listRecentPlays(contextSiteId(), limit, crateId));
    }
}
