package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.CoinHistoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.RecentLootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.WalletBreakdownResponseDTO;
import com.mirai.inventoryservice.models.review.ReviewDailyCount;
import com.mirai.inventoryservice.exceptions.LootboxException;
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
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LootboxService {

    private final ReviewDailyCountRepository reviewDailyCountRepository;
    private final CoinAdjustmentRepository coinAdjustmentRepository;
    private final LootboxPlayRepository lootboxPlayRepository;
    private final LootboxRepository lootboxRepository;
    private final LootboxTierRepository lootboxTierRepository;
    private final LootboxPrizeRepository lootboxPrizeRepository;
    private final UserRepository userRepository;
    private final LootboxTierLifecycle tierLifecycle;
    private final Random lootboxRandom;

    @PersistenceContext
    private EntityManager em;

    public record BalanceBreakdown(
            long balance,
            long reviewCredits,
            long totalAdjustments,
            long totalSpent,
            long totalExpired) {}

    public record PlayResult(LootboxPlay play, long newBalance) {}

    /**
     * Balance formula with 90-day expiry support:
     *
     *   balance = MAX(0, total_earned - MAX(total_spent, total_expired))
     *
     * Why MAX(spent, expired): spending and expiry both consume earnings, but the same
     * earning can only be consumed once. Whichever is larger dictates how much is gone;
     * the remainder is still spendable. This makes fresh earnings spendable even after
     * old coins lapsed unspent — solves the "phantom debt" trap a naive subtraction has.
     */
    @Transactional(readOnly = true)
    public BalanceBreakdown computeBalance(UUID userId) {
        // 3 round-trips: combined lifetime+expired sums per source, then the spent sum.
        // Sums coins_awarded (locked in per row at write time) so rate changes never
        // retroactively re-price already-earned credits.
        Object[] reviewTotals = firstRow(
                reviewDailyCountRepository.sumCoinTotalsByUserId(userId, LocalDate.now()));
        Object[] adjustmentTotals = firstRow(
                coinAdjustmentRepository.sumAdjustmentTotalsByUserId(userId, OffsetDateTime.now()));

        long totalReviewCredits = ((Number) reviewTotals[0]).longValue();
        long expiredReview      = ((Number) reviewTotals[1]).longValue();
        long totalAdjustments   = ((Number) adjustmentTotals[0]).longValue();
        long expiredAdjustments = ((Number) adjustmentTotals[1]).longValue();
        long totalSpent         = lootboxPlayRepository.sumCostByUserId(userId);

        long totalEarned  = totalReviewCredits + totalAdjustments;
        long totalExpired = expiredReview + expiredAdjustments;
        long raw          = totalEarned - Math.max(totalSpent, totalExpired);
        long balance      = Math.max(0L, raw);

        return new BalanceBreakdown(balance, totalReviewCredits, totalAdjustments, totalSpent, totalExpired);
    }

    /** COALESCE in the query guarantees a row, but defend against an empty result anyway. */
    private static Object[] firstRow(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[]{0L, 0L} : rows.get(0);
    }

    /**
     * Atomic play against a specific crate: validate crate is open, balance check vs.
     * crate.cost, weighted roll within that crate, persist play row.
     */
    @Transactional
    public PlayResult play(UUID userId, UUID crateId, String idempotencyKey) {
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

        OffsetDateTime now = OffsetDateTime.now();
        Lootbox crate = lootboxRepository.findByIdOpen(crateId, now)
                .orElseThrow(() -> new LootboxException(
                        "CRATE_UNAVAILABLE: crate is not currently open for play."));

        int cost = crate.getCost() == null ? 1 : crate.getCost();
        BalanceBreakdown bb = computeBalance(userId);
        if (bb.balance() < cost) {
            throw new LootboxException("INSUFFICIENT_BALANCE: not enough Pito Coins to play.");
        }

        LootboxPrize prize = rollAndClaimPrize(crate);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new LootboxException("User not found: " + userId));

        LootboxPlay play = LootboxPlay.builder()
                .user(user)
                .lootbox(crate)
                .lootboxNameSnapshot(crate.getName())
                .cost(cost)
                .prize(prize)
                .prizeNameSnapshot(prize.getName())
                .prizeDescriptionSnapshot(prize.getDescription())
                .prizeImageUrlSnapshot(prize.getImageUrl())
                .prizeTierNameSnapshot(prize.getTier().getName())
                .status("WON")
                .idempotencyKey(idempotencyKey)
                .build();
        lootboxPlayRepository.save(play);

        return new PlayResult(play, bb.balance() - cost);
    }

    /**
     * Roll-and-claim loop for cap-aware prize selection. Limited prizes (quantity != null)
     * are claimed via an atomic decrement; if the row was already depleted (returns 0),
     * we add it to the exclude set and re-roll. Unlimited prizes (quantity == null) are
     * accepted immediately.
     *
     * If every prize in the crate ends up excluded (the exclude-set fully covers the
     * rollable pool), rollPrize throws and we auto-close the crate so subsequent players
     * don't hit the same dead-end. The exception propagates uncharged because the play
     * row is saved only AFTER this method returns a prize.
     */
    private LootboxPrize rollAndClaimPrize(Lootbox crate) {
        Set<UUID> exclude = new HashSet<>();
        while (true) {
            LootboxPrize candidate;
            try {
                candidate = rollPrize(crate.getId(), exclude);
            } catch (LootboxException ex) {
                // Nothing left to award in this crate — close it so the next player sees
                // "unavailable" up front rather than hitting the same dead-end.
                crate.setActive(false);
                lootboxRepository.save(crate);
                throw ex;
            }
            if (candidate.getQuantity() == null) {
                return candidate;
            }
            int rows = lootboxPrizeRepository.decrementQuantity(candidate.getId());
            if (rows == 1) {
                // If this took the last copy, the SQL UPDATE also flipped active=false;
                // poke the tier-lifecycle helper so the tier deactivates + rebalances
                // when no active prizes remain. The helper is a no-op if any are left.
                tierLifecycle.maybeDeactivateTier(candidate.getTier().getId());
                return candidate;
            }
            // Lost the race or already depleted — drop it from contention and try again.
            exclude.add(candidate.getId());
        }
    }

    public LootboxPrize rollPrize(UUID crateId) {
        return rollPrize(crateId, Set.of());
    }

    /**
     * Weighted random pick over a crate's active tiers (and active prizes within them).
     * Probabilities of rollable tiers are normalized to sum to 1.0; empty/inactive tiers
     * are silently skipped so they don't "eat" rolls.
     *
     * `excludePrizeIds` is consulted to skip prizes whose stock was already depleted
     * during this play's roll loop (see rollAndClaimPrize). A tier becomes unrollable
     * for this attempt if every one of its active prizes is in the exclude set.
     */
    @Transactional(readOnly = true)
    public LootboxPrize rollPrize(UUID crateId, Set<UUID> excludePrizeIds) {
        List<LootboxTier> rollable = lootboxTierRepository.findRollableTiersByLootbox(crateId).stream()
                .filter(t -> hasRollablePrize(t.getId(), excludePrizeIds))
                .toList();
        if (rollable.isEmpty()) {
            throw new LootboxException("No prizes are currently available to roll for this crate.");
        }

        double total = rollable.stream()
                .mapToDouble(t -> t.getProbabilityPct().doubleValue())
                .sum();
        if (total <= 0.0) {
            throw new LootboxException("Active tier probabilities for this crate sum to zero.");
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

        List<LootboxPrize> prizes = lootboxPrizeRepository.findActiveByTierId(chosenTier.getId()).stream()
                .filter(p -> !excludePrizeIds.contains(p.getId()))
                .toList();
        if (prizes.isEmpty()) {
            throw new LootboxException("Selected tier has no active prizes.");
        }
        return prizes.get(lootboxRandom.nextInt(prizes.size()));
    }

    private boolean hasRollablePrize(UUID tierId, Set<UUID> excludePrizeIds) {
        if (excludePrizeIds.isEmpty()) return true;
        return lootboxPrizeRepository.findActiveByTierId(tierId).stream()
                .anyMatch(p -> !excludePrizeIds.contains(p.getId()));
    }

    /**
     * Sum of active tier probabilities WITHIN a crate, rounded to 2dp.
     * Service-layer invariant guard: per-crate sum must equal 100 to activate the crate.
     */
    @Transactional(readOnly = true)
    public BigDecimal sumActiveTierProbabilities(UUID crateId) {
        return lootboxTierRepository.findByLootboxIdOrderBySortOrderAscNameAsc(crateId).stream()
                .filter(LootboxTier::getActive)
                .map(LootboxTier::getProbabilityPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ----- Read-side mapping helpers used by both user + admin controllers -----

    /**
     * Player-facing catalog: only crates currently open, with their active tiers + prizes.
     */
    @Transactional(readOnly = true)
    public List<LootboxResponseDTO> getCatalog() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Lootbox> open = lootboxRepository.findOpen(now);
        if (open.isEmpty()) return List.of();

        // Include depleted (quantity = 0) prizes so the player UI can render "SOLD OUT"
        // cards alongside still-rollable ones.
        List<LootboxPrize> visiblePrizes = lootboxPrizeRepository.findAllActiveOrSoldOutWithTier();
        Map<UUID, List<LootboxPrize>> prizesByTier = visiblePrizes.stream()
                .collect(Collectors.groupingBy(p -> p.getTier().getId()));

        Map<UUID, List<LootboxTier>> tiersByCrate = loadTiersByCrate(open);

        return open.stream()
                .map(crate -> toLootboxDto(
                        crate,
                        tiersByCrate.getOrDefault(crate.getId(), List.of()),
                        prizesByTier))
                .toList();
    }

    /**
     * Admin catalog: ALL crates (including inactive / out-of-window) with ALL tiers and
     * prizes (active or not). Used by the admin UI to manage the full picture.
     */
    @Transactional(readOnly = true)
    public List<LootboxResponseDTO> getAdminCatalog() {
        List<Lootbox> all = lootboxRepository.findAllByOrderBySortOrderAscNameAsc();
        if (all.isEmpty()) return List.of();

        List<LootboxPrize> allPrizes = lootboxPrizeRepository.findAllWithTier();
        Map<UUID, List<LootboxPrize>> prizesByTier = allPrizes.stream()
                .collect(Collectors.groupingBy(p -> p.getTier().getId()));

        Map<UUID, List<LootboxTier>> tiersByCrate = loadTiersByCrate(all);

        return all.stream()
                .map(crate -> toAdminLootboxDto(
                        crate,
                        tiersByCrate.getOrDefault(crate.getId(), List.of()),
                        prizesByTier))
                .toList();
    }

    private Map<UUID, List<LootboxTier>> loadTiersByCrate(List<Lootbox> crates) {
        List<UUID> crateIds = crates.stream().map(Lootbox::getId).toList();
        return lootboxTierRepository
                .findByLootboxIdInOrderBySortOrderAscNameAsc(crateIds).stream()
                .collect(Collectors.groupingBy(LootboxTier::getLootboxId));
    }

    /** Admin variant: shows ALL tiers and ALL prizes (no active filter). */
    public LootboxResponseDTO toAdminLootboxDto(
            Lootbox crate,
            List<LootboxTier> tiers,
            Map<UUID, List<LootboxPrize>> prizesByTier) {
        List<LootboxTierResponseDTO> tierDtos = tiers.stream()
                .map(t -> toTierDto(t, prizesByTier.getOrDefault(t.getId(), List.of())))
                .toList();
        return LootboxResponseDTO.builder()
                .id(crate.getId())
                .name(crate.getName())
                .description(crate.getDescription())
                .imageUrl(crate.getImageUrl())
                .cost(crate.getCost())
                .startsAt(crate.getStartsAt())
                .endsAt(crate.getEndsAt())
                .sortOrder(crate.getSortOrder())
                .tiers(tierDtos)
                .build();
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
        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();

        for (Object[] row : reviewDailyCountRepository.findDailyCreditsByUser(userId)) {
            LocalDate date = (LocalDate) row[0];
            int reviewCount = ((Number) row[1]).intValue();
            int coinsAwarded = ((Number) row[2]).intValue();
            LocalDate expiresOn = (LocalDate) row[3];
            boolean expired = expiresOn != null && !expiresOn.isAfter(today);
            String reviewsLabel = reviewCount + " review" + (reviewCount == 1 ? "" : "s");
            // Surface coins separately so the UI can show "3 reviews (+6 coins)" when
            // a rate change makes per-row coin yield differ from the review count.
            String label = coinsAwarded == reviewCount
                    ? reviewsLabel
                    : reviewsLabel + " (+" + coinsAwarded + " coins)";
            entries.add(CoinHistoryEntryDTO.builder()
                    .kind("REVIEW_CREDIT")
                    .at(date.atStartOfDay().atOffset(ZoneOffset.UTC))
                    .delta(coinsAwarded)
                    .label(label)
                    .expired(expired)
                    .expiresAt(expiresOn != null
                            ? expiresOn.atStartOfDay().atOffset(ZoneOffset.UTC)
                            : null)
                    .build());
        }

        for (LootboxPlay p : lootboxPlayRepository.findByUserIdOrderByPlayedAtDesc(userId)) {
            String crateLabel = p.getLootboxNameSnapshot() != null
                    ? p.getLootboxNameSnapshot() + ": "
                    : "Lootbox: ";
            entries.add(CoinHistoryEntryDTO.builder()
                    .kind("PLAY")
                    .at(p.getPlayedAt())
                    .delta(-p.getCost())
                    .label(crateLabel + p.getPrizeNameSnapshot()
                            + " (" + p.getPrizeTierNameSnapshot() + ")")
                    .refId(p.getId())
                    .build());
        }

        for (CoinAdjustment a : coinAdjustmentRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            OffsetDateTime expiresOn = a.getExpiresAt();
            boolean expired = expiresOn != null && !expiresOn.isAfter(now);
            entries.add(CoinHistoryEntryDTO.builder()
                    .kind("ADJUSTMENT")
                    .at(a.getCreatedAt())
                    .delta(a.getDelta())
                    .label(a.getReason())
                    .refId(a.getId())
                    .expired(expired)
                    .expiresAt(expiresOn)
                    .build());
        }

        entries.sort(Comparator.comparing(CoinHistoryEntryDTO::at).reversed());
        return entries;
    }

    /**
     * Wallet breakdown for the "expiring soon" UI: groups upcoming expirations within
     * the next 30 days by date and surfaces the earliest expiry. Excludes plays (which
     * never expire) and rows that are already expired (they no longer count toward balance).
     */
    @Transactional(readOnly = true)
    public WalletBreakdownResponseDTO getWalletBreakdown(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(30);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime untilDt = until.atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<LocalDate, Integer> byDate = new java.util.TreeMap<>();

        for (ReviewDailyCount c : reviewDailyCountRepository.findExpiringSoon(userId, today, until)) {
            byDate.merge(c.getExpiresAt(), c.getCoinsAwarded(), Integer::sum);
        }
        for (CoinAdjustment a : coinAdjustmentRepository.findExpiringSoon(userId, now, untilDt)) {
            LocalDate day = a.getExpiresAt().toLocalDate();
            byDate.merge(day, a.getDelta(), Integer::sum);
        }

        List<WalletBreakdownResponseDTO.ExpirationBucket> buckets = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : byDate.entrySet()) {
            // Skip net-zero buckets (e.g. a +5 grant and -5 debit landing on the same day).
            if (e.getValue() <= 0) continue;
            buckets.add(WalletBreakdownResponseDTO.ExpirationBucket.builder()
                    .amount(e.getValue())
                    .expiresOn(e.getKey())
                    .build());
        }

        long balance = computeBalance(userId).balance();
        LocalDate nextExpiry = buckets.isEmpty() ? null : buckets.get(0).expiresOn();
        return WalletBreakdownResponseDTO.builder()
                .total(balance)
                .expiringSoon(buckets)
                .nextExpiryDate(nextExpiry)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RecentLootboxPlayResponseDTO> listRecentPlays(int limit, UUID crateId) {
        int cap = Math.max(1, Math.min(limit, 50));
        List<LootboxPlay> plays = crateId == null
                ? lootboxPlayRepository.findRecentPlaysWithAssociations(PageRequest.of(0, cap))
                : lootboxPlayRepository.findRecentPlaysByCrateWithAssociations(crateId, PageRequest.of(0, cap));
        return plays.stream()
                .map(LootboxService::toRecentPlayDto)
                .toList();
    }

    public static RecentLootboxPlayResponseDTO toRecentPlayDto(LootboxPlay play) {
        LootboxPrize prize = play.getPrize();
        LootboxTier tier = prize != null ? prize.getTier() : null;
        User user = play.getUser();
        return RecentLootboxPlayResponseDTO.builder()
                .id(play.getId())
                .prizeId(prize != null ? prize.getId() : null)
                .prizeName(play.getPrizeNameSnapshot())
                .prizeImageUrl(play.getPrizeImageUrlSnapshot())
                .tierName(play.getPrizeTierNameSnapshot())
                .tierColor(tier != null ? tier.getDisplayColor() : null)
                .userDisplay(toFirstNameLastInitial(user != null ? user.getFullName() : null))
                .playedAt(play.getPlayedAt())
                .build();
    }

    /**
     * "John Doe" -> "John D.", "Cher" -> "Cher", null/blank -> "Someone".
     * Keeps the ticker public-safe without leaking full last names.
     */
    static String toFirstNameLastInitial(String fullName) {
        if (fullName == null) return "Someone";
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) return "Someone";
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return parts[0];
        String last = parts[parts.length - 1];
        if (last.isEmpty()) return parts[0];
        return parts[0] + " " + Character.toUpperCase(last.charAt(0)) + ".";
    }

    /**
     * Builds the player-facing crate DTO using a prebuilt map of active/sold-out prizes
     * by tier. Tiers are shown if they're either active OR have at least one sold-out
     * prize — the latter keeps the "SOLD OUT" cards visible after a tier's last prize
     * auto-deactivated the tier on depletion. Tiers with no visible prizes (manually
     * inactive, never depleted) stay hidden.
     */
    public LootboxResponseDTO toLootboxDto(
            Lootbox crate,
            List<LootboxTier> tiers,
            Map<UUID, List<LootboxPrize>> activePrizesByTier) {
        List<LootboxTierResponseDTO> tierDtos = tiers.stream()
                .filter(t -> {
                    if (t.getActive()) return true;
                    return activePrizesByTier.getOrDefault(t.getId(), List.of()).stream()
                            .anyMatch(p -> p.getQuantity() != null && p.getQuantity() == 0);
                })
                .map(t -> toTierDto(t, activePrizesByTier.getOrDefault(t.getId(), List.of())))
                .toList();
        return LootboxResponseDTO.builder()
                .id(crate.getId())
                .name(crate.getName())
                .description(crate.getDescription())
                .imageUrl(crate.getImageUrl())
                .cost(crate.getCost())
                .startsAt(crate.getStartsAt())
                .endsAt(crate.getEndsAt())
                .sortOrder(crate.getSortOrder())
                .tiers(tierDtos)
                .build();
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
                .quantity(prize.getQuantity())
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
