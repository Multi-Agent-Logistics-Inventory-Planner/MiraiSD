package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.AdminCoinActivityDTO;
import com.mirai.inventoryservice.dtos.responses.CoinStatsResponseDTO;
import com.mirai.inventoryservice.dtos.responses.PlayerCoinRowDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregate queries that back the admin Coins-tab dashboard:
 * top-of-screen KPIs, the player-balances table, and the cross-user recent
 * activity feed.
 *
 * Per-user balance is derived (no materialized table) using the same formula as
 * LootboxService.computeBalance — MAX(0, earned − MAX(spent, expired)). These
 * queries scale that formula across all users in a single round trip via
 * grouped sub-aggregates joined to `users`. See V44__coin_time_axis_indexes.sql
 * for the time-axis indexes the activity feed relies on.
 *
 * If user count grows beyond a few hundred, replace the derived per-user balance
 * with a materialized `coin_balances` table refreshed on every adjust/play/credit
 * + a daily expiry-decrement job. Until then, derived is cheaper to maintain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinAdminDashboardService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Three top-of-screen KPIs. `circulation` and `holders` come from the same
     * grouped balance query; `granted7d` is a separate two-source SUM (positive
     * admin grants + review credits in the last 7 days, excluding spend).
     */
    @Transactional(readOnly = true)
    public CoinStatsResponseDTO getStats() {
        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        LocalDate sevenDaysAgoDate = today.minusDays(7);

        Object[] circRow = (Object[]) em.createNativeQuery("""
                WITH per_user AS (
                  SELECT
                    u.id AS user_id,
                    COALESCE(rdc.s, 0) + COALESCE(adj.s, 0) AS earned,
                    COALESCE(rdc_exp.s, 0) + COALESCE(adj_exp.s, 0) AS expired,
                    COALESCE(plays.s, 0) AS spent
                  FROM users u
                  LEFT JOIN (SELECT user_id, SUM(coins_awarded) AS s
                             FROM review_daily_counts GROUP BY user_id) rdc
                    ON rdc.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(coins_awarded) AS s
                             FROM review_daily_counts
                             WHERE expires_at <= :today
                             GROUP BY user_id) rdc_exp
                    ON rdc_exp.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(delta) AS s
                             FROM coin_adjustments GROUP BY user_id) adj
                    ON adj.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(delta) AS s
                             FROM coin_adjustments
                             WHERE expires_at <= :now
                             GROUP BY user_id) adj_exp
                    ON adj_exp.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(cost) AS s
                             FROM lootbox_plays GROUP BY user_id) plays
                    ON plays.user_id = u.id
                )
                SELECT
                  COALESCE(SUM(GREATEST(0, earned - GREATEST(spent, expired))), 0) AS circulation,
                  COUNT(*) FILTER (WHERE GREATEST(0, earned - GREATEST(spent, expired)) > 0) AS holders
                FROM per_user
                """)
                .setParameter("today", today)
                .setParameter("now", now)
                .getSingleResult();

        long circulation = ((Number) circRow[0]).longValue();
        int holders = ((Number) circRow[1]).intValue();

        Object grantedRow = em.createNativeQuery("""
                SELECT
                  COALESCE((SELECT SUM(delta) FROM coin_adjustments
                            WHERE delta > 0 AND created_at >= :since), 0)
                  + COALESCE((SELECT SUM(coins_awarded) FROM review_daily_counts
                              WHERE date >= :sinceDate), 0) AS granted
                """)
                .setParameter("since", sevenDaysAgo)
                .setParameter("sinceDate", sevenDaysAgoDate)
                .getSingleResult();

        long granted7d = ((Number) grantedRow).longValue();

        return CoinStatsResponseDTO.builder()
                .circulation(circulation)
                .holders(holders)
                .granted7d(granted7d)
                .build();
    }

    /**
     * Player rows: one per user who has ever participated in the coin economy
     * (has any earning, spend, or adjustment row). Sorted by balance desc, then
     * name asc as a stable tiebreaker. `lastChange*` describes the most recent
     * coin movement of any kind.
     *
     * `search`, `limit`, `offset` are accepted but only `limit`/`offset` are
     * honored today — search filters client-side (employee count is bounded by
     * org size, ~tens). When user count crosses ~300, switch on the WHERE clause
     * below; the contract doesn't change.
     */
    @Transactional(readOnly = true)
    public List<PlayerCoinRowDTO> getPlayers(String search, Integer limit, Integer offset) {
        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();

        StringBuilder sql = new StringBuilder("""
                WITH per_user AS (
                  SELECT
                    u.id        AS user_id,
                    u.full_name AS full_name,
                    u.email     AS email,
                    COALESCE(rdc.s, 0) + COALESCE(adj.s, 0) AS earned,
                    COALESCE(rdc_exp.s, 0) + COALESCE(adj_exp.s, 0) AS expired,
                    COALESCE(plays.s, 0) AS spent,
                    (rdc.s IS NOT NULL OR adj.s IS NOT NULL OR plays.s IS NOT NULL) AS has_history
                  FROM users u
                  LEFT JOIN (SELECT user_id, SUM(coins_awarded) AS s
                             FROM review_daily_counts GROUP BY user_id) rdc
                    ON rdc.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(coins_awarded) AS s
                             FROM review_daily_counts
                             WHERE expires_at <= :today
                             GROUP BY user_id) rdc_exp
                    ON rdc_exp.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(delta) AS s
                             FROM coin_adjustments GROUP BY user_id) adj
                    ON adj.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(delta) AS s
                             FROM coin_adjustments
                             WHERE expires_at <= :now
                             GROUP BY user_id) adj_exp
                    ON adj_exp.user_id = u.id
                  LEFT JOIN (SELECT user_id, SUM(cost) AS s
                             FROM lootbox_plays GROUP BY user_id) plays
                    ON plays.user_id = u.id
                ),
                last_change AS (
                  SELECT DISTINCT ON (user_id) user_id, delta, at
                  FROM (
                    SELECT user_id, delta, created_at AS at FROM coin_adjustments
                    UNION ALL
                    SELECT user_id, -cost AS delta, played_at AS at FROM lootbox_plays
                    UNION ALL
                    SELECT user_id, coins_awarded AS delta,
                           date::timestamptz AS at
                      FROM review_daily_counts WHERE coins_awarded > 0
                  ) ev
                  ORDER BY user_id, at DESC
                )
                SELECT pu.user_id, pu.full_name, pu.email,
                  GREATEST(0, pu.earned - GREATEST(pu.spent, pu.expired)) AS balance,
                  lc.delta AS last_change_delta,
                  lc.at    AS last_change_at
                FROM per_user pu
                LEFT JOIN last_change lc ON lc.user_id = pu.user_id
                WHERE pu.has_history
                """);

        boolean hasSearch = search != null && !search.isBlank();
        if (hasSearch) {
            sql.append(" AND (LOWER(pu.full_name) LIKE :q OR LOWER(pu.email) LIKE :q)");
        }
        sql.append(" ORDER BY balance DESC, pu.full_name ASC");
        if (limit != null && limit > 0) {
            sql.append(" LIMIT :lim");
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET :off");
        }

        var query = em.createNativeQuery(sql.toString())
                .setParameter("today", today)
                .setParameter("now", now);
        if (hasSearch) {
            query.setParameter("q", "%" + search.toLowerCase() + "%");
        }
        if (limit != null && limit > 0) {
            query.setParameter("lim", limit);
        }
        if (offset != null && offset > 0) {
            query.setParameter("off", offset);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        List<PlayerCoinRowDTO> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            UUID userId = (UUID) r[0];
            String fullName = (String) r[1];
            String email = (String) r[2];
            long balance = ((Number) r[3]).longValue();
            Integer lastDelta = r[4] != null ? ((Number) r[4]).intValue() : null;
            OffsetDateTime lastAt = r[5] != null ? toOffsetDateTime(r[5]) : null;
            out.add(PlayerCoinRowDTO.builder()
                    .userId(userId)
                    .fullName(fullName)
                    .email(email)
                    .balance(balance)
                    .lastChangeDelta(lastDelta)
                    .lastChangeAt(lastAt)
                    .build());
        }
        return out;
    }

    /**
     * Recent cross-user coin events for the activity feed. UNION of admin
     * adjustments and lootbox plays only — review credits are bulk-issued by the
     * 6 AM batch and would drown the feed (per-user review history remains
     * available via the History tab). Zero-delta events (free crates with
     * cost=0) are filtered: they don't move the economy and would be noise.
     */
    @Transactional(readOnly = true)
    public List<AdminCoinActivityDTO> getRecentActivity(int limit) {
        int cap = Math.max(1, Math.min(limit, 200));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT a.id, a.user_id, u.full_name, a.delta, a.label,
                       a.occurred_at, a.kind
                FROM (
                  ( SELECT id, user_id, delta, reason AS label,
                           created_at AS occurred_at, 'ADJUSTMENT' AS kind
                    FROM coin_adjustments
                    WHERE delta <> 0
                    ORDER BY created_at DESC
                    LIMIT :lim )
                  UNION ALL
                  ( SELECT id, user_id, -cost AS delta,
                           COALESCE(lootbox_name_snapshot, 'Lootbox')
                             || ': ' || prize_name_snapshot AS label,
                           played_at AS occurred_at, 'PLAY' AS kind
                    FROM lootbox_plays
                    WHERE cost > 0
                    ORDER BY played_at DESC
                    LIMIT :lim )
                ) a
                LEFT JOIN users u ON u.id = a.user_id
                ORDER BY a.occurred_at DESC
                LIMIT :lim
                """)
                .setParameter("lim", cap)
                .getResultList();

        List<AdminCoinActivityDTO> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(AdminCoinActivityDTO.builder()
                    .id((UUID) r[0])
                    .userId((UUID) r[1])
                    .userName((String) r[2])
                    .delta(((Number) r[3]).intValue())
                    .reason((String) r[4])
                    .occurredAt(toOffsetDateTime(r[5]))
                    .kind((String) r[6])
                    .build());
        }
        return out;
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.time.Instant ins) {
            return ins.atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }
}
