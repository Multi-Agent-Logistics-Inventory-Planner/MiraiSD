package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.lootbox.BulkUpdateTierProbabilitiesRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.CoinAdjustmentRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertLootboxRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertPrizeRequestDTO;
import com.mirai.inventoryservice.dtos.requests.lootbox.UpsertTierRequestDTO;
import com.mirai.inventoryservice.dtos.responses.CoinAdjustmentResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxAdminResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.UserCoinProfileResponseDTO;
import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.exceptions.UserNotFoundException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import com.mirai.inventoryservice.models.lootbox.Lootbox;
import com.mirai.inventoryservice.models.lootbox.LootboxPlay;
import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.CoinAdjustmentRepository;
import com.mirai.inventoryservice.repositories.LootboxPlayRepository;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxRepository;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin operations: crate CRUD, tier/prize CRUD with per-crate sum-to-100 invariant +
 * auto-rebalance when the last active prize in a tier is deactivated, redemption queue,
 * and signed coin adjustments with a balance >= 0 floor.
 *
 * Per-crate semantics: every tier belongs to exactly one crate, and the sum-to-100
 * probability rule is enforced WITHIN that crate. Two crates' tier probabilities are
 * fully independent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LootboxAdminService {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");
    private static final BigDecimal EPSILON = new BigDecimal("0.05");

    private final LootboxRepository lootboxRepository;
    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;
    private final LootboxPlayRepository lootboxPlayRepository;
    private final CoinAdjustmentRepository coinAdjustmentRepository;
    private final UserRepository userRepository;
    private final LootboxService lootboxService;

    // ----- Crate (Lootbox) CRUD -----

    @Transactional(readOnly = true)
    public List<LootboxAdminResponseDTO> listCrates() {
        List<Lootbox> crates = lootboxRepository.findAllByOrderBySortOrderAscNameAsc();
        if (crates.isEmpty()) return List.of();

        // Batch: one query for all tiers across all crates, one grouped-count query for
        // their active prizes. Replaces a 1 + C + C*T pattern with 1 + 1 + 1.
        List<UUID> crateIds = crates.stream().map(Lootbox::getId).toList();
        List<LootboxTier> allTiers = lootboxTierRepository
                .findByLootboxIdInOrderBySortOrderAscNameAsc(crateIds);
        Map<UUID, List<LootboxTier>> tiersByCrate = allTiers.stream()
                .collect(Collectors.groupingBy(LootboxTier::getLootboxId));
        Map<UUID, Long> prizeCountByTier = activePrizeCountsByTierId(allTiers);

        return crates.stream()
                .map(c -> toCrateAdminDto(
                        c,
                        tiersByCrate.getOrDefault(c.getId(), List.of()),
                        prizeCountByTier))
                .toList();
    }

    /**
     * Single-crate convenience for create/update paths: loads the crate's tiers + grouped
     * active-prize counts in two queries, then delegates. Same shape as `listCrates`
     * scaled down — kept here so the per-crate paths don't drift back to the N+1 pattern.
     */
    private LootboxAdminResponseDTO toCrateAdminDto(Lootbox crate) {
        List<LootboxTier> tiers = lootboxTierRepository
                .findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId());
        return toCrateAdminDto(crate, tiers, activePrizeCountsByTierId(tiers));
    }

    private Map<UUID, Long> activePrizeCountsByTierId(List<LootboxTier> tiers) {
        if (tiers.isEmpty()) return Collections.emptyMap();
        List<UUID> tierIds = tiers.stream().map(LootboxTier::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : lootboxPrizeRepository.countActiveGroupedByTierIds(tierIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional
    public LootboxAdminResponseDTO createCrate(UpsertLootboxRequestDTO req) {
        validateWindow(req.startsAt(), req.endsAt());
        Lootbox crate = Lootbox.builder()
                .name(req.name())
                .description(req.description())
                .imageUrl(req.imageUrl())
                .cost(req.cost() != null ? req.cost() : 1)
                .startsAt(req.startsAt())
                .endsAt(req.endsAt())
                .active(req.active() != null ? req.active() : true)
                .siteId(req.siteId())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .build();
        lootboxRepository.save(crate);
        return toCrateAdminDto(crate);
    }

    @Transactional
    public LootboxAdminResponseDTO updateCrate(UUID id, UpsertLootboxRequestDTO req) {
        Lootbox crate = lootboxRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Crate not found: " + id));
        if (req.name() != null) crate.setName(req.name());
        if (req.description() != null) crate.setDescription(req.description());
        if (req.imageUrl() != null) crate.setImageUrl(req.imageUrl());
        if (req.cost() != null) crate.setCost(req.cost());
        if (req.startsAt() != null || req.endsAt() != null) {
            OffsetDateTime startsAt = req.startsAt() != null ? req.startsAt() : crate.getStartsAt();
            OffsetDateTime endsAt   = req.endsAt()   != null ? req.endsAt()   : crate.getEndsAt();
            validateWindow(startsAt, endsAt);
            crate.setStartsAt(startsAt);
            crate.setEndsAt(endsAt);
        }
        if (req.active() != null) {
            if (req.active()) assertCrateProbabilitiesSumIs100(id);
            crate.setActive(req.active());
        }
        if (req.siteId() != null) crate.setSiteId(req.siteId());
        if (req.sortOrder() != null) crate.setSortOrder(req.sortOrder());
        lootboxRepository.save(crate);
        return toCrateAdminDto(crate);
    }

    @Transactional
    public void deleteCrate(UUID id) {
        Lootbox crate = lootboxRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Crate not found: " + id));
        long playCount = lootboxPlayRepository.countByLootboxId(id);
        if (playCount > 0) {
            throw new LootboxException(
                    "Cannot delete crate with " + playCount + " play(s) recorded; deactivate it instead.");
        }
        // No plays reference it; safe to hard-delete tiers + prizes + crate.
        List<LootboxTier> tiers = lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(id);
        for (LootboxTier t : tiers) {
            lootboxPrizeRepository.deleteAll(lootboxPrizeRepository.findActiveByTierId(t.getId()));
        }
        lootboxTierRepository.deleteAll(tiers);
        lootboxRepository.delete(crate);
    }

    private void validateWindow(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new LootboxException("Crate ends_at must be after starts_at.");
        }
    }

    // ----- Tier CRUD -----

    @Transactional
    public LootboxTierResponseDTO createTier(UpsertTierRequestDTO req) {
        if (req.lootboxId() == null) {
            throw new LootboxException("lootboxId is required when creating a tier.");
        }
        Lootbox crate = lootboxRepository.findById(req.lootboxId())
                .orElseThrow(() -> new EntityNotFoundException("Crate not found: " + req.lootboxId()));
        lootboxTierRepository.findByLootboxIdAndName(req.lootboxId(), req.name()).ifPresent(t -> {
            throw new LootboxException("Tier already exists in this crate: " + req.name());
        });
        LootboxTier tier = LootboxTier.builder()
                .lootbox(crate)
                .name(req.name())
                .probabilityPct(req.probabilityPct())
                .displayColor(req.displayColor())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .active(req.active() != null ? req.active() : true)
                .build();
        lootboxTierRepository.save(tier);
        assertCrateProbabilitiesSumIs100(req.lootboxId());
        return LootboxService.toTierDto(tier, List.of());
    }

    @Transactional
    public LootboxTierResponseDTO updateTier(UUID id, UpsertTierRequestDTO req) {
        LootboxTier tier = lootboxTierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + id));
        UUID crateId = tier.getLootbox().getId();
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
        assertCrateProbabilitiesSumIs100(crateId);
        List<LootboxPrize> prizes = lootboxPrizeRepository.findActiveByTierId(id);
        return LootboxService.toTierDto(tier, prizes);
    }

    /**
     * Apply a batch of tier probability changes WITHIN a single crate, in one transaction.
     * Validates the per-crate sum-to-100 invariant once at the end so admins can shift
     * weight between tiers (e.g. 70->50 + 20->40) without tripping the per-tier guard.
     *
     * Probability is the source of truth for tier active state within the batch: prob > 0
     * implies the tier is live (requires at least one active prize), prob == 0 deactivates.
     */
    @Transactional
    public List<LootboxTierResponseDTO> bulkUpdateTierProbabilities(
            BulkUpdateTierProbabilitiesRequestDTO req) {
        UUID crateId = req.lootboxId();
        for (BulkUpdateTierProbabilitiesRequestDTO.TierProbability change : req.tiers()) {
            LootboxTier tier = lootboxTierRepository.findById(change.id())
                    .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + change.id()));
            if (!tier.getLootbox().getId().equals(crateId)) {
                throw new LootboxException(
                        "Tier " + tier.getName() + " does not belong to crate " + crateId);
            }
            boolean wantsActive = change.probabilityPct().compareTo(BigDecimal.ZERO) > 0;
            if (wantsActive && lootboxPrizeRepository.countActiveByTierId(tier.getId()) == 0) {
                throw new LootboxException(
                        "Cannot give probability to tier " + tier.getName() + " with no active prizes.");
            }
            tier.setProbabilityPct(change.probabilityPct());
            tier.setActive(wantsActive);
        }
        lootboxTierRepository.flush();
        assertCrateProbabilitiesSumIs100(crateId);

        List<LootboxTier> tiers = lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crateId);
        return tiers.stream()
                .map(t -> LootboxService.toTierDto(t, lootboxPrizeRepository.findActiveByTierId(t.getId())))
                .toList();
    }

    @Transactional
    public void deleteTier(UUID id) {
        LootboxTier tier = lootboxTierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + id));
        UUID crateId = tier.getLootbox().getId();
        if (lootboxPrizeRepository.countActiveByTierId(id) > 0) {
            throw new LootboxException("Cannot delete a tier with active prizes; deactivate prizes first.");
        }
        // Soft-delete by deactivation; hard-deleting would break FK on lootbox_plays / prizes.
        tier.setActive(false);
        tier.setProbabilityPct(BigDecimal.ZERO);
        lootboxTierRepository.save(tier);
        rebalanceActiveTiers(crateId);
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

        if (wasActive && (!prize.getActive() || !prize.getTier().getId().equals(oldTierId))) {
            maybeDeactivateTier(oldTierId);
        }
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
     * When an inactive tier gains an active prize, mark it active so it's visible to the
     * sum-to-100 invariant. Probability stays at 0 — admin redistributes weight explicitly.
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
        UUID crateId = tier.getLootbox().getId();
        tier.setActive(false);
        tier.setProbabilityPct(BigDecimal.ZERO);
        lootboxTierRepository.save(tier);
        rebalanceActiveTiers(crateId);
    }

    /**
     * Proportionally redistribute probabilities across active tiers of a crate so they sum
     * to 100. If no active tiers remain in the crate, leaves the state alone (next roll
     * fails; admin must add a prize + activate a tier to recover).
     */
    private void rebalanceActiveTiers(UUID crateId) {
        List<LootboxTier> active = lootboxTierRepository
                .findByLootboxIdOrderBySortOrderAscNameAsc(crateId).stream()
                .filter(LootboxTier::getActive)
                .toList();
        if (active.isEmpty()) return;

        BigDecimal sum = active.stream()
                .map(LootboxTier::getProbabilityPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) == 0) {
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

    private void assertCrateProbabilitiesSumIs100(UUID crateId) {
        BigDecimal sum = lootboxService.sumActiveTierProbabilities(crateId);
        boolean hasActive = lootboxTierRepository
                .findByLootboxIdOrderBySortOrderAscNameAsc(crateId).stream()
                .anyMatch(LootboxTier::getActive);
        if (!hasActive) return;
        if (sum.subtract(TOTAL).abs().compareTo(EPSILON) > 0) {
            throw new LootboxException(
                    "Active tier probabilities for this crate must sum to 100.00 (currently " + sum + ").");
        }
    }

    // ----- Redemption -----

    @Transactional(readOnly = true)
    public Page<LootboxPlayResponseDTO> getPendingRedemptions(Pageable pageable) {
        return getPlaysByStatus("WON", pageable);
    }

    /**
     * Paginated plays by status. Powers the redemption queue's Pending vs Redeemed
     * tabs; reject any value outside the documented set so a typo'd query param
     * doesn't silently return zero rows.
     */
    @Transactional(readOnly = true)
    public Page<LootboxPlayResponseDTO> getPlaysByStatus(String status, Pageable pageable) {
        if (!"WON".equals(status) && !"REDEEMED".equals(status)) {
            throw new LootboxException("Unsupported status: " + status);
        }
        return lootboxPlayRepository
                .findByStatusOrderByPlayedAtDesc(status, pageable)
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
                        .totalExpired(bb.totalExpired())
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

    private LootboxAdminResponseDTO toCrateAdminDto(
            Lootbox crate,
            List<LootboxTier> tiers,
            Map<UUID, Long> prizeCountByTier) {
        int prizeCount = 0;
        for (LootboxTier t : tiers) {
            prizeCount += prizeCountByTier.getOrDefault(t.getId(), 0L).intValue();
        }
        return LootboxAdminResponseDTO.builder()
                .id(crate.getId())
                .name(crate.getName())
                .description(crate.getDescription())
                .imageUrl(crate.getImageUrl())
                .cost(crate.getCost())
                .startsAt(crate.getStartsAt())
                .endsAt(crate.getEndsAt())
                .active(crate.getActive())
                .siteId(crate.getSiteId())
                .sortOrder(crate.getSortOrder())
                .tierCount(tiers.size())
                .prizeCount(prizeCount)
                .createdAt(crate.getCreatedAt())
                .updatedAt(crate.getUpdatedAt())
                .build();
    }
}
