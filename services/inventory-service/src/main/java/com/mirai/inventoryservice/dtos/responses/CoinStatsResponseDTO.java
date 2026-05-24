package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

/**
 * Admin Coins-tab top-of-screen KPIs.
 *
 * `circulation` = sum across users of MAX(0, earned − MAX(spent, expired)) using the
 *   same per-user balance formula as LootboxService.computeBalance.
 * `holders`     = count of users whose balance > 0.
 * `granted7d`   = positive coin issuance over the trailing 7 days:
 *                 SUM(coin_adjustments.delta WHERE delta > 0)
 *                 + SUM(review_daily_counts.coins_awarded).
 *                 Excludes spend (plays).
 */
@Builder
public record CoinStatsResponseDTO(
        long circulation,
        int holders,
        long granted7d
) {}
