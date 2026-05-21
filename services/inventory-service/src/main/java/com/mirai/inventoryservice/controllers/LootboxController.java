package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.requests.lootbox.PlayLootboxRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CoinHistoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxBalanceResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.PlayLootboxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.RecentLootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.WalletBreakdownResponseDTO;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.repositories.UserRepository;
import com.mirai.inventoryservice.services.IdempotencyService;
import com.mirai.inventoryservice.services.LootboxService;
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
import java.util.Map;
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

    @GetMapping("/balance")
    public ResponseEntity<LootboxBalanceResponseDTO> getBalance(Authentication auth) {
        UUID userId = resolveUserId(auth);
        LootboxService.BalanceBreakdown bb = lootboxService.computeBalance(userId);
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
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxService.getWalletBreakdown(userId));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<LootboxResponseDTO>> getCatalog() {
        return ResponseEntity.ok(lootboxService.getCatalog());
    }

    @PostMapping("/play")
    public ResponseEntity<PlayLootboxResponseDTO> play(
            Authentication auth,
            @Valid @RequestBody PlayLootboxRequestDTO body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID userId = resolveUserId(auth);

        var cached = idempotencyService.get(userId, idempotencyKey, PlayLootboxResponseDTO.class);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        LootboxService.PlayResult result = lootboxService.play(userId, body.crateId(), idempotencyKey);
        PlayLootboxResponseDTO response = PlayLootboxResponseDTO.builder()
                .play(LootboxService.toPlayDto(result.play()))
                .newBalance(result.newBalance())
                .build();
        idempotencyService.put(userId, idempotencyKey, response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-prizes")
    public ResponseEntity<List<LootboxPlayResponseDTO>> getMyPrizes(Authentication auth) {
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxService.getUserPrizes(userId));
    }

    @GetMapping("/my-history")
    public ResponseEntity<List<CoinHistoryEntryDTO>> getMyHistory(Authentication auth) {
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(lootboxService.getUserHistory(userId));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<RecentLootboxPlayResponseDTO>> getRecentPlays(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(lootboxService.listRecentPlays(limit));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = extractEmail(auth);
        if (email == null) {
            // Dev fallback: when path-level auth is disabled there's no principal to map.
            // Use the first ADMIN user as the actor so the feature can be exercised E2E.
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .findFirst()
                    .map(User::getId)
                    .orElseThrow(() -> new UserNotFoundException(
                            "No authenticated user and no ADMIN user in DB to fall back to."));
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
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
