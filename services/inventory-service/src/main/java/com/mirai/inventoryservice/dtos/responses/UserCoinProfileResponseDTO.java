package com.mirai.inventoryservice.dtos.responses;

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
