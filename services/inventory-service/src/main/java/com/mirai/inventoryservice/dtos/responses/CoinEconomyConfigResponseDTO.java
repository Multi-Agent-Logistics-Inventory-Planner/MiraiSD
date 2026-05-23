package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin payload for the editable coin-economy config. `nextFetchHint` is a
 * pre-baked explanation string so the UI doesn't have to know about the 6 AM
 * batch schedule — the backend owns that messaging.
 */
@Builder
public record CoinEconomyConfigResponseDTO(
        int reviewCoinRate,
        OffsetDateTime updatedAt,
        UUID updatedByUserId,
        String updatedByName,
        String nextFetchHint
) {}
