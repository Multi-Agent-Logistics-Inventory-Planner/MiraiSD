package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.config.LootboxConfig;
import com.mirai.inventoryservice.dtos.responses.CoinHistoryEntryDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPlayResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxPrizeResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxResponseDTO;
import com.mirai.inventoryservice.dtos.responses.LootboxTierResponseDTO;
import com.mirai.inventoryservice.dtos.responses.RecentLootboxPlayResponseDTO;
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
    private final LootboxRepository lootboxRepository;
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

        LootboxPrize prize = rollPrize(crate.getId());
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
     * Weighted random pick over a crate's active tiers (and active prizes within them).
     * Probabilities of rollable tiers are normalized to sum to 1.0; empty/inactive tiers
     * are silently skipped so they don't "eat" rolls.
     */
    @Transactional(readOnly = true)
    public LootboxPrize rollPrize(UUID crateId) {
        List<LootboxTier> rollable = lootboxTierRepository.findRollableTiersByLootbox(crateId);
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

        List<LootboxPrize> prizes = lootboxPrizeRepository.findActiveByTierId(chosenTier.getId());
        if (prizes.isEmpty()) {
            // findRollableTiersByLootbox should guarantee this can't happen, but defend
            // against a race where a prize was deactivated between query and now.
            throw new LootboxException("Selected tier has no active prizes.");
        }
        return prizes.get(lootboxRandom.nextInt(prizes.size()));
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

        List<LootboxPrize> activePrizes = lootboxPrizeRepository.findAllActiveWithTier();
        Map<UUID, List<LootboxPrize>> prizesByTier = activePrizes.stream()
                .collect(Collectors.groupingBy(p -> p.getTier().getId()));

        return open.stream()
                .map(crate -> toLootboxDto(crate, prizesByTier))
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

        return all.stream()
                .map(crate -> toAdminLootboxDto(crate, prizesByTier))
                .toList();
    }

    /** Admin variant: shows ALL tiers and ALL prizes (no active filter). */
    public LootboxResponseDTO toAdminLootboxDto(Lootbox crate, Map<UUID, List<LootboxPrize>> prizesByTier) {
        List<LootboxTier> tiers = lootboxTierRepository
                .findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId());
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

    @Transactional(readOnly = true)
    public List<RecentLootboxPlayResponseDTO> listRecentPlays(int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        return lootboxPlayRepository.findTop50ByOrderByPlayedAtDesc().stream()
                .limit(cap)
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
     * Builds the player-facing crate DTO using a prebuilt map of active prizes by tier.
     * Tiers are filtered to active-only since this is the player view.
     */
    public LootboxResponseDTO toLootboxDto(Lootbox crate, Map<UUID, List<LootboxPrize>> activePrizesByTier) {
        List<LootboxTier> tiers = lootboxTierRepository
                .findByLootboxIdOrderBySortOrderAscNameAsc(crate.getId());
        List<LootboxTierResponseDTO> tierDtos = tiers.stream()
                .filter(LootboxTier::getActive)
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
