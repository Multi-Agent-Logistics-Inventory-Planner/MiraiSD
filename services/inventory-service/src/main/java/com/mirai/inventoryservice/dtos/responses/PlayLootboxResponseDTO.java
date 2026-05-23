package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

@Builder
public record PlayLootboxResponseDTO(
        LootboxPlayResponseDTO play,
        long newBalance
) {}
