package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.repositories.CoinAdjustmentRepository;
import com.mirai.inventoryservice.repositories.LootboxPlayRepository;
import com.mirai.inventoryservice.repositories.LootboxPrizeRepository;
import com.mirai.inventoryservice.repositories.LootboxRepository;
import com.mirai.inventoryservice.repositories.LootboxTierRepository;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Mock-backed tests for the Phase 2 balance formula:
 *
 *   balance = MAX(0, total_earned - MAX(total_spent, total_expired))
 *
 * Each test sets the repository sums to a scenario from the grill (Q6) and asserts
 * the formula matches the expected user-visible balance. These guard the formula
 * against regressions — particularly the "fresh earnings absorbed by phantom debt"
 * bug that a naive `earned - spent - expired` would produce.
 *
 * The repos return combined `[total, expired]` tuples in one round-trip (see
 * `sumCoinTotalsByUserId` / `sumAdjustmentTotalsByUserId`); the tiny `totals()` helper
 * keeps the per-test overrides terse.
 */
@ExtendWith(MockitoExtension.class)
class LootboxBalanceFormulaTest {

    @Mock private ReviewDailyCountRepository reviewDailyCountRepository;
    @Mock private CoinAdjustmentRepository coinAdjustmentRepository;
    @Mock private LootboxPlayRepository lootboxPlayRepository;
    @Mock private LootboxRepository lootboxRepository;
    @Mock private LootboxTierRepository lootboxTierRepository;
    @Mock private LootboxPrizeRepository lootboxPrizeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private LootboxService service;

    private final UUID userId = UUID.randomUUID();
    /** A fixed Random so the @InjectMocks satisfies the field. */
    @SuppressWarnings("unused")
    private final Random rng = new Random(0);

    private static List<Object[]> totals(long total, long expired) {
        // List.<Object[]>of(...) so the Object[] varargs doesn't spread into List<Object>.
        return List.<Object[]>of(new Object[]{total, expired});
    }

    @BeforeEach
    void setUp() {
        // Default all sums to 0 so individual tests only override what they care about.
        // Marked lenient because not every test exercises every path.
        lenient().when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any(LocalDate.class)))
                .thenReturn(totals(0L, 0L));
        lenient().when(coinAdjustmentRepository.sumAdjustmentTotalsByUserId(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(totals(0L, 0L));
        lenient().when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(0L);
    }

    @Test
    @DisplayName("Earn 100, no spend, nothing expired → 100")
    void freshEarnings() {
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(100L, 0L));
        assertEquals(100L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Earn 100, spend 80, nothing expired → 20")
    void earnAndSpend() {
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(100L, 0L));
        when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(80L);
        assertEquals(20L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Earn 100, spend 0, all 100 expired → 0 (unspent lapse is correct)")
    void unspentCoinsExpire() {
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(100L, 100L));
        assertEquals(0L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Earn 100, spend 80, all 100 expired → 0 (the 20 unspent lapses)")
    void partiallySpentExpiry() {
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(100L, 100L));
        when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(80L);
        assertEquals(0L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Earn 100, spend 100, all 100 expired → 0 (no double-charge → never negative)")
    void fullySpentBeforeExpiryDoesntDoubleCharge() {
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(100L, 100L));
        when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(100L);
        assertEquals(0L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Earn 100, spend 80, all 100 expired, then earn 50 fresh → 50 (NO phantom debt)")
    void freshEarningsAfterPastExpiry() {
        // Lifetime sums: 150 earned (100 old + 50 new), 80 spent, 100 of the earnings expired.
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(150L, 100L));
        when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(80L);
        // 150 - MAX(80, 100) = 150 - 100 = 50  ✓ (the new 50 is fully spendable)
        assertEquals(50L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Interleaved: earn 100 spend 100, then earn 50 spend 30 - 100 expire → 20")
    void interleavedEarningsAndExpiry() {
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(150L, 100L));
        when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(130L);
        // 150 - MAX(130, 100) = 150 - 130 = 20  ✓
        assertEquals(20L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Negative adjustment + expiry: net is symmetric and clamps at zero")
    void negativeAdjustmentExpiry() {
        // Earned 50 from reviews, debited 20 via adjustment, nothing spent yet, nothing expired.
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(50L, 0L));
        when(coinAdjustmentRepository.sumAdjustmentTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(-20L, 0L));
        // total_earned = 30, no expiry yet → balance 30
        assertEquals(30L, service.computeBalance(userId).balance());

        // Now both the +50 review credits and the -20 adjustment expire.
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(50L, 50L));
        when(coinAdjustmentRepository.sumAdjustmentTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(-20L, -20L));
        // total_earned = 30, total_expired = 30, total_spent = 0 → 30 - 30 = 0  ✓
        assertEquals(0L, service.computeBalance(userId).balance());
    }

    @Test
    @DisplayName("Balance sums coins_awarded, not review_count (Phase 3 rate-aware contract)")
    void balance_usesCoinsAwarded_notReviewCount() {
        // The repository now exposes coins_awarded sums via sumCoinTotalsByUserId (tuple
        // [total, expired]), not raw review_count. If the service ever regressed to
        // multiplying review_count by the current rate, this test would still pass —
        // what we're guarding is that the formula reads the coins_awarded-shaped query.
        // With the rate having been 2 historically, 10 reviews granted 20 coins.
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(20L, 0L));
        assertEquals(20L, service.computeBalance(userId).balance());
        assertEquals(20L, service.computeBalance(userId).reviewCredits());
    }

    @Test
    @DisplayName("Balance is clamped at zero — never negative under any inputs")
    void balanceClampsAtZero() {
        // Pathological-ish: lots spent, more than ever earned (shouldn't happen under guards,
        // but the formula must still clamp).
        when(reviewDailyCountRepository.sumCoinTotalsByUserId(eq(userId), any()))
                .thenReturn(totals(10L, 0L));
        when(lootboxPlayRepository.sumCostByUserId(userId)).thenReturn(1000L);
        assertEquals(0L, service.computeBalance(userId).balance());
    }
}
