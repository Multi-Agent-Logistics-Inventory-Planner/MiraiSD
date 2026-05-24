package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cross-user coin activity entry for the admin Coins-tab "Recent activity" feed.
 * Sources: coin_adjustments (ADJUSTMENT) and lootbox_plays (PLAY). Excludes
 * review_daily_counts — those are bulk-issued by the 6 AM batch and would drown
 * the feed; per-user review history is available via the History tab.
 */
@Builder
public record AdminCoinActivityDTO(
        UUID id,
        UUID userId,
        String userName,
        int delta,
        String reason,
        OffsetDateTime occurredAt,
        String kind
) {}
