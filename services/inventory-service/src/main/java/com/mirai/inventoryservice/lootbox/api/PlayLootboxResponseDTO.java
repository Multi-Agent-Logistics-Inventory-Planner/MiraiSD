package com.mirai.inventoryservice.lootbox.api;

import lombok.Builder;

@Builder
public record PlayLootboxResponseDTO(
        LootboxPlayResponseDTO play,
        long newBalance
) {}
