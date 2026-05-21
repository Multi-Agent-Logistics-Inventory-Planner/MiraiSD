package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A crate as seen by admins: includes inactive flag and the forward-compat site_id.
 * The /admin/crates endpoints return this shape; the player-facing /catalog uses
 * LootboxResponseDTO (which omits the admin-only fields and embeds tiers/prizes).
 */
@Builder
public record LootboxAdminResponseDTO(
        UUID id,
        String name,
        String description,
        String imageUrl,
        Integer cost,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean active,
        UUID siteId,
        Integer sortOrder,
        int tierCount,
        int prizeCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
