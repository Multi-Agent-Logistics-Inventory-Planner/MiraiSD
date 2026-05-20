package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record LootboxPlayResponseDTO(
        UUID id,
        UUID userId,
        String userName,
        UUID prizeId,
        String prizeName,
        String prizeDescription,
        String prizeImageUrl,
        String prizeTierName,
        Integer cost,
        String status,
        OffsetDateTime playedAt,
        OffsetDateTime redeemedAt,
        UUID redeemedByUserId,
        String redeemedByName
) {}
