package com.mirai.inventoryservice.lootbox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin crate create/update payload. NULL startsAt / endsAt mean unbounded on that side.
 * cost = 0 is valid (free-spin promo crates). {@code siteId} is accepted for backward
 * compatibility but is IGNORED server-side (.specs/phase-7-kuji-lootbox T-2/T-3): a crate's
 * site always comes from the caller's authorized site context, never from the request body.
 * Kept in the DTO shape rather than removed here since dropping it is a contract change that
 * belongs with T-4's OpenAPI/client regeneration.
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
