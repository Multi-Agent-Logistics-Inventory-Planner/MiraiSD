package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record LootboxTierResponseDTO(
        UUID id,
        String name,
        BigDecimal probabilityPct,
        String displayColor,
        Integer sortOrder,
        boolean active,
        List<LootboxPrizeResponseDTO> prizes
) {}
