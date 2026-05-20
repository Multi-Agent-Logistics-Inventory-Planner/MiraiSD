package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.lootbox.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.CoinAdjustmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertTierRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CoinAdjustmentResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserCoinProfileResponseDTO;
import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import com.mirai.inventoryservice.models.lootbox.LootboxPlay;
import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.CoinAdjustmentRepository;
import com.mirai.inventoryservice.repositories.LootboxPlayRepository;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxTierRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin operations: tier/prize CRUD with the sum-to-100 invariant + auto-rebalance
 * when the last active prize in a tier is deactivated, redemption queue, and signed
 * coin adjustments with a balance >= 0 floor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LootboxAdminService {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");
    private static final BigDecimal EPSILON = new BigDecimal("0.05");

    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;
    private final LootboxPlayRepository lootboxPlayRepository;
    private final CoinAdjustmentRepository coinAdjustmentRepository;
    private final UserRepository userRepository;
    private final LootboxService lootboxService;

    // ----- Tier CRUD -----

    @Transactional
    public LootboxTierResponseDTO createTier(UpsertTierRequestDTO req) {
        lootboxTierRepository.findByName(req.name()).ifPresent(t -> {
            throw new LootboxException("Tier already exists: " + req.name());
        });
        LootboxTier tier = LootboxTier.builder()
                .name(req.name())
                .probabilityPct(req.probabilityPct())
                .displayColor(req.displayColor())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .active(req.active() != null ? req.active() : true)
                .build();
        lootboxTierRepository.save(tier);
        assertActiveSumIs100();
        return LootboxService.toTierDto(tier, List.of());
    }

    @Transactional
    public LootboxTierResponseDTO updateTier(UUID id, UpsertTierRequestDTO req) {
        LootboxTier tier = lootboxTierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + id));
        if (req.name() != null) tier.setName(req.name());
        if (req.probabilityPct() != null) tier.setProbabilityPct(req.probabilityPct());
        if (req.displayColor() != null) tier.setDisplayColor(req.displayColor());
        if (req.sortOrder() != null) tier.setSortOrder(req.sortOrder());
        if (req.active() != null) {
            if (req.active() && lootboxPrizeRepository.countActiveByTierId(id) == 0) {
                throw new LootboxException("Cannot activate a tier with no active prizes.");
            }
            tier.setActive(req.active());
        }
        lootboxTierRepository.save(tier);
        assertActiveSumIs100();
        List<LootboxPrize> prizes = lootboxPrizeRepository.findActiveByTierId(id);
        return LootboxService.toTierDto(tier, prizes);
    }

    /**
     * Apply a batch of tier probability changes in a single transaction. Only validates
     * the sum-to-100 invariant once at the end, so callers can shift weight between tiers
     * (e.g. 70->50 + 20->40) without tripping the per-tier guard.
     *
     * Treats probability as the source of truth for tier active state: prob > 0 implies
     * the tier is live (requires at least one active prize), prob == 0 deactivates. This
     * lets admins re-activate a tier purely by redistributing weight to it.
     */
    @Transactional
    public List<LootboxTierResponseDTO> bulkUpdateTierProbabilities(
            BulkUpdateTierProbabilitiesRequestDTO req) {
        for (BulkUpdateTierProbabilitiesRequestDTO.TierProbability change : req.tiers()) {
            LootboxTier tier = lootboxTierRepository.findById(change.id())
                    .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + change.id()));
            boolean wantsActive = change.probabilityPct().compareTo(BigDecimal.ZERO) > 0;
            if (wantsActive && lootboxPrizeRepository.countActiveByTierId(tier.getId()) == 0) {
                throw new LootboxException(
                        "Cannot give probability to tier " + tier.getName() + " with no active prizes.");
            }
            tier.setProbabilityPct(change.probabilityPct());
            tier.setActive(wantsActive);
        }
        lootboxTierRepository.flush();
        assertActiveSumIs100();
        return lootboxService.getCatalog(false);
    }

    @Transactional
    public void deleteTier(UUID id) {
        LootboxTier tier = lootboxTierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + id));
        if (lootboxPrizeRepository.countActiveByTierId(id) > 0) {
            throw new LootboxException("Cannot delete a tier with active prizes; deactivate prizes first.");
        }
        // Soft-delete by deactivation; hard-deleting would break FK on lootbox_plays / prizes.
        tier.setActive(false);
        tier.setProbabilityPct(BigDecimal.ZERO);
        lootboxTierRepository.save(tier);
        rebalanceActiveTiers();
    }

    // ----- Prize CRUD -----

    @Transactional
    public LootboxPrizeResponseDTO createPrize(UpsertPrizeRequestDTO req) {
        LootboxTier tier = lootboxTierRepository.findById(req.tierId())
                .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + req.tierId()));
        LootboxPrize prize = LootboxPrize.builder()
                .tier(tier)
                .name(req.name())
                .description(req.description())
                .imageUrl(req.imageUrl())
                .active(req.active() != null ? req.active() : true)
                .build();
        lootboxPrizeRepository.save(prize);
        if (prize.getActive()) maybeReactivateTier(tier);
        return LootboxService.toPrizeDto(prize, tier);
    }

    @Transactional
    public LootboxPrizeResponseDTO updatePrize(UUID id, UpsertPrizeRequestDTO req) {
        LootboxPrize prize = lootboxPrizeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prize not found: " + id));
        boolean wasActive = prize.getActive();
        UUID oldTierId = prize.getTier().getId();

        if (req.tierId() != null && !req.tierId().equals(oldTierId)) {
            LootboxTier newTier = lootboxTierRepository.findById(req.tierId())
                    .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + req.tierId()));
            prize.setTier(newTier);
        }
        if (req.name() != null) prize.setName(req.name());
        if (req.description() != null) prize.setDescription(req.description());
        if (req.imageUrl() != null) prize.setImageUrl(req.imageUrl());
        if (req.active() != null) prize.setActive(req.active());
        lootboxPrizeRepository.save(prize);

        // If we just made the prize inactive (or moved it out) and its old tier now has zero
        // active prizes, deactivate the tier and rebalance the rest.
        if (wasActive && (!prize.getActive() || !prize.getTier().getId().equals(oldTierId))) {
            maybeDeactivateTier(oldTierId);
        }
        // Symmetric: if the prize is now active and its (current) tier is inactive, re-activate
        // the tier so admins can redistribute weight to it without it silently being skipped.
        if (prize.getActive()) {
            maybeReactivateTier(prize.getTier());
        }

        return LootboxService.toPrizeDto(prize, prize.getTier());
    }

    @Transactional
    public void deletePrize(UUID id) {
        LootboxPrize prize = lootboxPrizeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prize not found: " + id));
        UUID tierId = prize.getTier().getId();
        prize.setActive(false);
        lootboxPrizeRepository.save(prize);
        maybeDeactivateTier(tierId);
    }

    /**
     * Counterpart to {@link #maybeDeactivateTier}: when an inactive tier gains an active
     * prize, mark it active so it's visible to the sum-to-100 invariant. Probability is
     * left at 0 (set when it was deactivated) — the admin redistributes weight explicitly.
     */
    private void maybeReactivateTier(LootboxTier tier) {
        if (tier.getActive()) return;
        tier.setActive(true);
        lootboxTierRepository.save(tier);
    }

    private void maybeDeactivateTier(UUID tierId) {
        if (lootboxPrizeRepository.countActiveByTierId(tierId) > 0) return;
        LootboxTier tier = lootboxTierRepository.findById(tierId).orElse(null);
        if (tier == null || !tier.getActive()) return;
        tier.setActive(false);
        tier.setProbabilityPct(BigDecimal.ZERO);
        lootboxTierRepository.save(tier);
        rebalanceActiveTiers();
    }

    /**
     * Proportionally redistribute probabilities across active tiers so they sum to 100.
     * If no active tiers remain, leaves the state alone (the next roll will fail; admin
     * must add a prize + activate a tier to recover).
     */
    private void rebalanceActiveTiers() {
        List<LootboxTier> active = lootboxTierRepository.findAll().stream()
                .filter(LootboxTier::getActive)
                .toList();
        if (active.isEmpty()) return;

        BigDecimal sum = active.stream()
                .map(LootboxTier::getProbabilityPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            // Edge case: all active tier %s collapsed to 0. Split evenly.
            BigDecimal each = TOTAL.divide(new BigDecimal(active.size()), 2, RoundingMode.HALF_UP);
            for (LootboxTier t : active) t.setProbabilityPct(each);
        } else {
            for (LootboxTier t : active) {
                BigDecimal scaled = t.getProbabilityPct()
                        .multiply(TOTAL)
                        .divide(sum, 2, RoundingMode.HALF_UP);
                t.setProbabilityPct(scaled);
            }
        }
        // Fix rounding drift: nudge the largest-share tier so the sum is exactly 100.
        BigDecimal newSum = active.stream()
                .map(LootboxTier::getProbabilityPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal drift = TOTAL.subtract(newSum);
        if (drift.compareTo(BigDecimal.ZERO) != 0) {
            LootboxTier largest = active.stream()
                    .max((a, b) -> a.getProbabilityPct().compareTo(b.getProbabilityPct()))
                    .orElse(active.get(0));
            largest.setProbabilityPct(largest.getProbabilityPct().add(drift));
        }
        lootboxTierRepository.saveAll(active);
    }

    private void assertActiveSumIs100() {
        BigDecimal sum = lootboxService.sumActiveTierProbabilities();
        // Allow inactive-only state (e.g. mid-setup) to skip the check.
        boolean hasActive = lootboxTierRepository.findAll().stream().anyMatch(LootboxTier::getActive);
        if (!hasActive) return;
        if (sum.subtract(TOTAL).abs().compareTo(EPSILON) > 0) {
            throw new LootboxException(
                    "Active tier probabilities must sum to 100.00 (currently " + sum + ").");
        }
    }

    // ----- Redemption -----

    @Transactional(readOnly = true)
    public Page<LootboxPlayResponseDTO> getPendingRedemptions(Pageable pageable) {
        return lootboxPlayRepository
                .findByStatusOrderByPlayedAtDesc("WON", pageable)
                .map(LootboxService::toPlayDto);
    }

    @Transactional
    public LootboxPlayResponseDTO markRedeemed(UUID playId, UUID adminUserId) {
        LootboxPlay play = lootboxPlayRepository.findById(playId)
                .orElseThrow(() -> new EntityNotFoundException("Play not found: " + playId));
        if (!"WON".equals(play.getStatus())) {
            throw new LootboxException("Prize is not pending redemption (status=" + play.getStatus() + ").");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found: " + adminUserId));
        play.setStatus("REDEEMED");
        play.setRedeemedAt(OffsetDateTime.now());
        play.setRedeemedBy(admin);
        lootboxPlayRepository.save(play);
        return LootboxService.toPlayDto(play);
    }

    // ----- Coin adjustments -----

    @Transactional
    public CoinAdjustmentResponseDTO createAdjustment(CoinAdjustmentRequestDTO req, UUID grantedByUserId) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + req.userId()));
        User admin = userRepository.findById(grantedByUserId)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found: " + grantedByUserId));

        if (req.delta() < 0) {
            long currentBalance = lootboxService.computeBalance(req.userId()).balance();
            if (currentBalance + req.delta() < 0) {
                throw new LootboxException(
                        "INSUFFICIENT_BALANCE: cannot deduct " + (-req.delta())
                                + " coins (current balance " + currentBalance + ").");
            }
        }

        CoinAdjustment adjustment = CoinAdjustment.builder()
                .user(user)
                .delta(req.delta())
                .reason(req.reason())
                .grantedBy(admin)
                .build();
        coinAdjustmentRepository.save(adjustment);
        return toAdjustmentDto(adjustment);
    }

    // ----- Admin per-user profile -----

    @Transactional(readOnly = true)
    public UserCoinProfileResponseDTO getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        LootboxService.BalanceBreakdown bb = lootboxService.computeBalance(userId);
        List<LootboxPlayResponseDTO> plays = lootboxPlayRepository
                .findByUserIdOrderByPlayedAtDesc(userId).stream()
                .map(LootboxService::toPlayDto)
                .toList();
        List<CoinAdjustmentResponseDTO> adjustments = new ArrayList<>();
        for (CoinAdjustment a : coinAdjustmentRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            adjustments.add(toAdjustmentDto(a));
        }
        return UserCoinProfileResponseDTO.builder()
                .userId(user.getId())
                .userName(user.getFullName())
                .userEmail(user.getEmail())
                .balance(com.mirai.inventoryservice.dtos.responses.LootboxBalanceResponseDTO.builder()
                        .balance(bb.balance())
                        .reviewCredits(bb.reviewCredits())
                        .totalAdjustments(bb.totalAdjustments())
                        .totalSpent(bb.totalSpent())
                        .build())
                .plays(plays)
                .adjustments(adjustments)
                .build();
    }

    private CoinAdjustmentResponseDTO toAdjustmentDto(CoinAdjustment a) {
        User user = a.getUser();
        User admin = a.getGrantedBy();
        return CoinAdjustmentResponseDTO.builder()
                .id(a.getId())
                .userId(user != null ? user.getId() : null)
                .userName(user != null ? user.getFullName() : null)
                .delta(a.getDelta())
                .reason(a.getReason())
                .grantedByUserId(admin != null ? admin.getId() : null)
                .grantedByName(admin != null ? admin.getFullName() : null)
                .createdAt(a.getCreatedAt())
                .build();
    }
}
