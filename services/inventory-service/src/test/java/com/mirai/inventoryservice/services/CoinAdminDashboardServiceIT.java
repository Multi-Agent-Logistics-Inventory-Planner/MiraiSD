package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.AdminCoinActivityDTO;
import com.mirai.inventoryservice.dtos.responses.CoinStatsResponseDTO;
import com.mirai.inventoryservice.dtos.responses.PlayerCoinRowDTO;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import com.mirai.inventoryservice.models.lootbox.LootboxPlay;
import com.mirai.inventoryservice.models.review.ReviewDailyCount;
import com.mirai.inventoryservice.repositories.CoinAdjustmentRepository;
import com.mirai.inventoryservice.repositories.LootboxPlayRepository;
import com.mirai.inventoryservice.repositories.ReviewDailyCountRepository;
import com.mirai.inventoryservice.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres integration tests for the admin Coins-tab dashboard. The native
 * SQL uses Postgres-only features (GREATEST, FILTER, DISTINCT ON, ::timestamptz)
 * so H2 won't catch regressions — these run against the Testcontainers Postgres.
 */
@Transactional
class CoinAdminDashboardServiceIT extends BaseKafkaIntegrationTest {

    @Autowired private CoinAdminDashboardService dashboardService;
    @Autowired private UserRepository userRepository;
    @Autowired private CoinAdjustmentRepository coinAdjustmentRepository;
    @Autowired private LootboxPlayRepository lootboxPlayRepository;
    @Autowired private ReviewDailyCountRepository reviewDailyCountRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("stats: circulation sums per-user balances, holders counts > 0, granted7d covers admin + review")
    void statsAggregate() {
        User admin = persistUser("Admin", "admin-stats@test.com", UserRole.ADMIN);
        User alice = persistUser("Alice", "alice-stats@test.com", UserRole.EMPLOYEE);
        User bob   = persistUser("Bob",   "bob-stats@test.com",   UserRole.EMPLOYEE);
        User cara  = persistUser("Cara",  "cara-stats@test.com",  UserRole.EMPLOYEE);

        // Alice: +10 adjustment, +5 review credit (5 days ago) → balance 15
        adjust(alice, admin, 10, "grant");
        reviewCredit(alice, LocalDate.now().minusDays(5), 5);

        // Bob: +20 adjustment, then 7 coin play → balance 13
        adjust(bob, admin, 20, "grant");
        play(bob, 7);

        // Cara: 0 balance (no activity) — should NOT count toward holders
        // No rows for cara.

        // Older-than-7d adjustment that must NOT appear in granted7d.
        CoinAdjustment old = adjust(alice, admin, 100, "old-grant");
        // Forcibly age it past the 7-day window.
        em.createNativeQuery("UPDATE coin_adjustments SET created_at = :ts WHERE id = :id")
                .setParameter("ts", OffsetDateTime.now().minusDays(10))
                .setParameter("id", old.getId())
                .executeUpdate();

        em.flush();
        em.clear();

        CoinStatsResponseDTO stats = dashboardService.getStats();

        // alice 15 + old 100 (still in circulation) + bob 13 = 128
        assertThat(stats.circulation()).isEqualTo(128L);
        assertThat(stats.holders()).isEqualTo(2);
        // granted7d: alice +10 + bob +20 + alice review +5 = 35. Old +100 excluded.
        assertThat(stats.granted7d()).isEqualTo(35L);
    }

    @Test
    @DisplayName("players: sorted by balance desc, lastChange reflects most recent event, no-history users excluded")
    void playersListing() {
        User admin  = persistUser("Admin", "admin-players@test.com", UserRole.ADMIN);
        User top    = persistUser("Top",    "top@test.com",    UserRole.EMPLOYEE);
        User middle = persistUser("Middle", "middle@test.com", UserRole.EMPLOYEE);
        User bottom = persistUser("Bottom", "bottom@test.com", UserRole.EMPLOYEE);
        User ghost  = persistUser("Ghost",  "ghost@test.com",  UserRole.EMPLOYEE);

        adjust(top,    admin, 100, "grant");
        adjust(middle, admin, 50,  "grant");
        adjust(bottom, admin, 1,   "grant");
        // 'ghost' has no activity at all.

        // most-recent event for `middle` should be a -3 play, not the +50 grant.
        play(middle, 3);

        em.flush();
        em.clear();

        List<PlayerCoinRowDTO> rows = dashboardService.getPlayers(null, null, null);

        assertThat(rows).extracting(PlayerCoinRowDTO::fullName)
                .containsExactly("Top", "Middle", "Bottom");
        assertThat(rows).extracting(PlayerCoinRowDTO::balance)
                .containsExactly(100L, 47L, 1L);

        PlayerCoinRowDTO middleRow = rows.stream()
                .filter(r -> r.fullName().equals("Middle")).findFirst().orElseThrow();
        assertThat(middleRow.lastChangeDelta()).isEqualTo(-3);
        assertThat(middleRow.lastChangeAt()).isNotNull();
    }

    @Test
    @DisplayName("activity: returns latest N across adjustments + plays in time order; excludes review credits")
    void activityFeed() {
        User admin = persistUser("Admin", "admin-act@test.com", UserRole.ADMIN);
        User u     = persistUser("Activity User", "act@test.com", UserRole.EMPLOYEE);

        adjust(u, admin, 10, "first");
        adjust(u, admin, 20, "second");
        play(u, 5);
        adjust(u, admin, 30, "third");

        // Review credit must NOT appear in the admin activity feed.
        reviewCredit(u, LocalDate.now().minusDays(1), 7);

        em.flush();
        em.clear();

        List<AdminCoinActivityDTO> feed = dashboardService.getRecentActivity(20);

        assertThat(feed).hasSize(4);
        assertThat(feed.get(0).reason()).isEqualTo("third");
        assertThat(feed.get(0).kind()).isEqualTo("ADJUSTMENT");
        assertThat(feed.get(0).delta()).isEqualTo(30);
        // Play row exists with negative delta
        assertThat(feed).anyMatch(r -> r.kind().equals("PLAY") && r.delta() == -5);
        // Review-credit kind must not be present at all
        assertThat(feed).noneMatch(r -> "REVIEW_CREDIT".equals(r.kind()));
    }

    @Test
    @DisplayName("activity: limit caps the result size")
    void activityLimitHonored() {
        User admin = persistUser("Admin", "admin-lim@test.com", UserRole.ADMIN);
        User u     = persistUser("U", "u-lim@test.com", UserRole.EMPLOYEE);
        for (int i = 0; i < 5; i++) adjust(u, admin, 1, "row-" + i);

        em.flush();
        em.clear();

        assertThat(dashboardService.getRecentActivity(3)).hasSize(3);
    }

    @Test
    @DisplayName("players: search filters by name and email case-insensitively")
    void playersSearch() {
        User admin = persistUser("Admin", "admin-srch@test.com", UserRole.ADMIN);
        User alpha = persistUser("Alpha One",  "alpha@example.com", UserRole.EMPLOYEE);
        User beta  = persistUser("Beta Two",   "beta@example.com",  UserRole.EMPLOYEE);
        adjust(alpha, admin, 10, "g");
        adjust(beta,  admin, 10, "g");

        em.flush();
        em.clear();

        assertThat(dashboardService.getPlayers("alp", null, null))
                .extracting(PlayerCoinRowDTO::fullName).containsExactly("Alpha One");
        assertThat(dashboardService.getPlayers("BETA", null, null))
                .extracting(PlayerCoinRowDTO::fullName).containsExactly("Beta Two");
        // Match on email
        assertThat(dashboardService.getPlayers("example.com", null, null))
                .hasSize(2);
    }

    // ----- helpers -----

    private User persistUser(String name, String email, UserRole role) {
        User u = User.builder()
                .fullName(name)
                .email(email)
                .role(role)
                .build();
        return userRepository.saveAndFlush(u);
    }

    private CoinAdjustment adjust(User user, User admin, int delta, String reason) {
        CoinAdjustment a = CoinAdjustment.builder()
                .user(user)
                .delta(delta)
                .reason(reason)
                .grantedBy(admin)
                .build();
        return coinAdjustmentRepository.saveAndFlush(a);
    }

    private void play(User user, int cost) {
        LootboxPlay p = LootboxPlay.builder()
                .user(user)
                .lootboxNameSnapshot("TestCrate")
                .cost(cost)
                .prizeNameSnapshot("Prize")
                .prizeTierNameSnapshot("Common")
                .status("WON")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        lootboxPlayRepository.saveAndFlush(p);
    }

    private void reviewCredit(User user, LocalDate date, int coins) {
        ReviewDailyCount r = ReviewDailyCount.builder()
                .user(user)
                .date(date)
                .reviewCount(coins)
                .coinsAwarded(coins)
                .build();
        reviewDailyCountRepository.saveAndFlush(r);
    }
}
