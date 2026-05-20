package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.util.UUID;

@Builder
public record LootboxPrizeResponseDTO(
        UUID id,
        String name,
        String description,
        String imageUrl,
        UUID tierId,
        String tierName,
        String tierColor,
        boolean active
) {}
