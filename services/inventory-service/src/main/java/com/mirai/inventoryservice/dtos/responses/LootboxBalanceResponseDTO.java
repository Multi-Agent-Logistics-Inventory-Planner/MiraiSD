package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

@Builder
public record LootboxBalanceResponseDTO(
        long balance,
        long reviewCredits,
        long totalAdjustments,
        long totalSpent,
        long totalExpired
) {}
