package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Tier lifecycle rules shared by admin edits and the play path (cap-driven depletion).
 *
 * Why a separate component: both LootboxService (play -> auto-deactivate on cap hit) and
 * LootboxAdminService (delete/update prize) need to trigger the same rebalance. Keeping
 * these methods here breaks the circular dependency that would otherwise form between
 * the two services.
 */
@Component
@RequiredArgsConstructor
public class LootboxTierLifecycle {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;

    /**
     * When an inactive tier gains an active prize, mark it active so it's visible to the
     * sum-to-100 invariant. Probability stays at 0 — admin redistributes weight explicitly.
     */
    public void maybeReactivateTier(LootboxTier tier) {
        if (tier.getActive()) return;
        tier.setActive(true);
        lootboxTierRepository.save(tier);
    }

    /**
     * If a tier has no active prizes left (last one depleted or deactivated), flip it
     * inactive, zero its weight, and proportionally redistribute the freed weight across
     * the crate's remaining active tiers.
     */
    public void maybeDeactivateTier(UUID tierId) {
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
    public void rebalanceActiveTiers(UUID crateId) {
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
}
