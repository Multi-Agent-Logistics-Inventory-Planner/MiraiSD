package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin crate create/update payload. NULL startsAt / endsAt mean unbounded on that side.
 * cost = 0 is valid (free-spin promo crates). site_id is forward-compat: backend persists
 * the value but does not yet use it for filtering.
 */
public record UpsertLootboxRequestDTO(
        @NotBlank String name,
        String description,
        String imageUrl,
        @PositiveOrZero Integer cost,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Boolean active,
        UUID siteId,
        Integer sortOrder
) {}
