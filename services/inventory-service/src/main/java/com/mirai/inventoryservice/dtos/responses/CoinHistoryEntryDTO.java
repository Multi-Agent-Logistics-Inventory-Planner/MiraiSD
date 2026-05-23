package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Unified row for the user-facing Coin History panel. `kind` is one of
 * REVIEW_CREDIT, PLAY, ADJUSTMENT.
 */
@Builder
public record CoinHistoryEntryDTO(
        String kind,
        OffsetDateTime at,
        int delta,
        String label,
        UUID refId,
        /** True when this earning row's expires_at is at or before "now"; null for PLAY rows. */
        Boolean expired,
        /** When this earning expires (or expired). Null for PLAY rows. */
        OffsetDateTime expiresAt
) {}
