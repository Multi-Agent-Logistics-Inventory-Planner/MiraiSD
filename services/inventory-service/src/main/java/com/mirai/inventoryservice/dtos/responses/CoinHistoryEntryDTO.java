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
        UUID refId
) {}
