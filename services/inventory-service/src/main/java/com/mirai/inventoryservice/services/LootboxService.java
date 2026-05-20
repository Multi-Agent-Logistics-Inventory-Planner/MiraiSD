package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.config.LootboxConfig;
import com.mirai.inventoryservice.dtos.responses.CoinHistoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.exceptions.LootboxException;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import com.mirai.inventoryservice.models.lootbox.LootboxPlay;
import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import com.mirai.inventoryservice.repositories.CoinAdjustmentRepository;
import com.mirai.inventoryservice.repositories.LootboxPlayRepository;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxTierRepository;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LootboxService {

    private final ReviewDailyCountRepository reviewDailyCountRepository;
    private final CoinAdjustmentRepository coinAdjustmentRepository;
    private final LootboxPlayRepository lootboxPlayRepository;
    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;
    private final UserRepository userRepository;
    private final LootboxConfig lootboxConfig;
    private final Random lootboxRandom;

    @PersistenceContext
    private EntityManager em;

    public record BalanceBreakdown(long balance, long reviewCredits, long totalAdjustments, long totalSpent) {}

    public record PlayResult(LootboxPlay play, long newBalance) {}

    @Transactional(readOnly = true)
    public BalanceBreakdown computeBalance(UUID userId) {
        long reviewCredits = reviewDailyCountRepository.sumReviewCountByUserSince(
                userId, lootboxConfig.getLaunchDate());
        long totalAdjustments = coinAdjustmentRepository.sumDeltaByUserId(userId);
        long totalSpent = lootboxPlayRepository.sumCostByUserId(userId);
        long balance = reviewCredits + totalAdjustments - totalSpent;
        return new BalanceBreakdown(balance, reviewCredits, totalAdjustments, totalSpent);
    }

    /**
     * Atomic play: balance check, weighted roll, persist play row. Caller is responsible
     * for the surrounding @Transactional boundary and idempotency cache.
     */
    @Transactional
    public PlayResult play(UUID userId, String idempotencyKey) {
        // Per-user serialization without needing an existing row to lock.
        // hashtext(uuid::text) collapses into a 32-bit int suitable for pg_advisory_xact_lock.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:k))")
                .setParameter("k", userId.toString())
                .getSingleResult();

        // Re-check idempotency after acquiring the lock: a concurrent request with the same
        // key may have already inserted while we were blocked.
        if (idempotencyKey != null) {
            Optional<LootboxPlay> existing = lootboxPlayRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                long balance = computeBalance(userId).balance();
                return new PlayResult(existing.get(), balance);
            }
        }

        BalanceBreakdown bb = computeBalance(userId);
        if (bb.balance() < 1) {
            throw new LootboxException("INSUFFICIENT_BALANCE: not enough Pito Coins to play.");
        }

        LootboxPrize prize = rollPrize();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new LootboxException("User not found: " + userId));

        LootboxPlay play = LootboxPlay.builder()
                .user(user)
                .cost(1)
                .prize(prize)
                .prizeNameSnapshot(prize.getName())
                .prizeDescriptionSnapshot(prize.getDescription())
                .prizeImageUrlSnapshot(prize.getImageUrl())
                .prizeTierNameSnapshot(prize.getTier().getName())
                .status("WON")
                .idempotencyKey(idempotencyKey)
                .build();
        lootboxPlayRepository.save(play);

        return new PlayResult(play, bb.balance() - 1);
    }

    /**
     * Weighted random pick over active tiers (and active prizes within them).
     * Probabilities of rollable tiers are normalized to sum to 1.0; empty/inactive tiers
     * are silently skipped so they don't "eat" rolls.
     */
    @Transactional(readOnly = true)
    public LootboxPrize rollPrize() {
        List<LootboxTier> rollable = lootboxTierRepository.findRollableTiers();
        if (rollable.isEmpty()) {
            throw new LootboxException("No prizes are currently available to roll.");
        }

        double total = rollable.stream()
                .mapToDouble(t -> t.getProbabilityPct().doubleValue())
                .sum();
        if (total <= 0.0) {
            throw new LootboxException("Active tier probabilities sum to zero.");
        }

        double pick = lootboxRandom.nextDouble() * total;
        double cumulative = 0.0;
        LootboxTier chosenTier = rollable.get(rollable.size() - 1);
        for (LootboxTier t : rollable) {
            cumulative += t.getProbabilityPct().doubleValue();
            if (pick < cumulative) {
                chosenTier = t;
                break;
            }
        }

        List<LootboxPrize> prizes = lootboxPrizeRepository.findActiveByTierId(chosenTier.getId());
        if (prizes.isEmpty()) {
            // findRollableTiers should guarantee this can't happen, but defend against
            // a race where a prize was deactivated between query and now.
            throw new LootboxException("Selected tier has no active prizes.");
        }
        return prizes.get(lootboxRandom.nextInt(prizes.size()));
    }

    /**
     * Sum of active tier probabilities, rounded to 2dp. Service-layer invariant guard:
     * active tier %s should always equal 100 (within a small epsilon for rounding).
     */
    @Transactional(readOnly = true)
    public BigDecimal sumActiveTierProbabilities() {
        return lootboxTierRepository.findAll().stream()
                .filter(LootboxTier::getActive)
                .map(LootboxTier::getProbabilityPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ----- Read-side mapping helpers used by both user + admin controllers -----

    @Transactional(readOnly = true)
    public List<LootboxTierResponseDTO> getCatalog(boolean activeOnly) {
        List<LootboxTier> tiers = lootboxTierRepository.findAllByOrderBySortOrderAscNameAsc();
        List<LootboxPrize> prizes = activeOnly
                ? lootboxPrizeRepository.findAllActiveWithTier()
                : lootboxPrizeRepository.findAllWithTier();
        Map<UUID, List<LootboxPrize>> byTier = prizes.stream()
                .collect(Collectors.groupingBy(p -> p.getTier().getId()));
        return tiers.stream()
                .filter(t -> !activeOnly || t.getActive())
                .map(t -> toTierDto(t, byTier.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LootboxPlayResponseDTO> getUserPrizes(UUID userId) {
        return lootboxPlayRepository.findByUserIdOrderByPlayedAtDesc(userId).stream()
                .map(LootboxService::toPlayDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CoinHistoryEntryDTO> getUserHistory(UUID userId) {
        List<CoinHistoryEntryDTO> entries = new ArrayList<>();

        LocalDate launch = lootboxConfig.getLaunchDate();
        for (Object[] row : reviewDailyCountRepository.findDailyCreditsByUserSince(userId, launch)) {
            LocalDate date = (LocalDate) row[0];
            int count = ((Number) row[1]).intValue();
            entries.add(CoinHistoryEntryDTO.builder()
                    .kind("REVIEW_CREDIT")
                    .at(date.atStartOfDay().atOffset(ZoneOffset.UTC))
                    .delta(count)
                    .label(count + " review" + (count == 1 ? "" : "s"))
                    .build());
        }

        for (LootboxPlay p : lootboxPlayRepository.findByUserIdOrderByPlayedAtDesc(userId)) {
            entries.add(CoinHistoryEntryDTO.builder()
                    .kind("PLAY")
                    .at(p.getPlayedAt())
                    .delta(-p.getCost())
                    .label("Lootbox: " + p.getPrizeNameSnapshot()
                            + " (" + p.getPrizeTierNameSnapshot() + ")")
                    .refId(p.getId())
                    .build());
        }

        for (CoinAdjustment a : coinAdjustmentRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            entries.add(CoinHistoryEntryDTO.builder()
                    .kind("ADJUSTMENT")
                    .at(a.getCreatedAt())
                    .delta(a.getDelta())
                    .label(a.getReason())
                    .refId(a.getId())
                    .build());
        }

        entries.sort(Comparator.comparing(CoinHistoryEntryDTO::at).reversed());
        return entries;
    }

    public static LootboxTierResponseDTO toTierDto(LootboxTier tier, List<LootboxPrize> prizes) {
        return LootboxTierResponseDTO.builder()
                .id(tier.getId())
                .name(tier.getName())
                .probabilityPct(tier.getProbabilityPct())
                .displayColor(tier.getDisplayColor())
                .sortOrder(tier.getSortOrder())
                .active(tier.getActive())
                .prizes(prizes.stream().map(p -> toPrizeDto(p, tier)).toList())
                .build();
    }

    public static LootboxPrizeResponseDTO toPrizeDto(LootboxPrize prize, LootboxTier tier) {
        return LootboxPrizeResponseDTO.builder()
                .id(prize.getId())
                .name(prize.getName())
                .description(prize.getDescription())
                .imageUrl(prize.getImageUrl())
                .tierId(tier.getId())
                .tierName(tier.getName())
                .tierColor(tier.getDisplayColor())
                .active(prize.getActive())
                .build();
    }

    public static LootboxPlayResponseDTO toPlayDto(LootboxPlay play) {
        User u = play.getUser();
        User r = play.getRedeemedBy();
        return LootboxPlayResponseDTO.builder()
                .id(play.getId())
                .userId(u != null ? u.getId() : null)
                .userName(u != null ? u.getFullName() : null)
                .prizeId(play.getPrize() != null ? play.getPrize().getId() : null)
                .prizeName(play.getPrizeNameSnapshot())
                .prizeDescription(play.getPrizeDescriptionSnapshot())
                .prizeImageUrl(play.getPrizeImageUrlSnapshot())
                .prizeTierName(play.getPrizeTierNameSnapshot())
                .cost(play.getCost())
                .status(play.getStatus())
                .playedAt(play.getPlayedAt())
                .redeemedAt(play.getRedeemedAt())
                .redeemedByUserId(r != null ? r.getId() : null)
                .redeemedByName(r != null ? r.getFullName() : null)
                .build();
    }
}
