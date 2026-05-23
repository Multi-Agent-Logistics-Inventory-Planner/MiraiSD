package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A crate as seen by players: name, cost, optional window, and the active tiers/prizes
 * inside it. Crates returned to the player-facing /catalog are only those currently open.
 */
@Builder
public record LootboxResponseDTO(
        UUID id,
        String name,
        String description,
        String imageUrl,
        Integer cost,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Integer sortOrder,
        List<LootboxTierResponseDTO> tiers
) {}
