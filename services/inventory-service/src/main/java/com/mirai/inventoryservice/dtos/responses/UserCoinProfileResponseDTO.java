package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.lootbox.api.LootboxBalanceResponseDTO;
import com.mirai.inventoryservice.lootbox.api.LootboxPlayResponseDTO;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserCoinProfileResponseDTO(
        UUID userId,
        String userName,
        String userEmail,
        LootboxBalanceResponseDTO balance,
        List<LootboxPlayResponseDTO> plays,
        List<CoinAdjustmentResponseDTO> adjustments
) {}
