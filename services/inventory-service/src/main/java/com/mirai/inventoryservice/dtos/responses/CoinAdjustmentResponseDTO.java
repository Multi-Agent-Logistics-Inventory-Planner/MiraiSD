package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record CoinAdjustmentResponseDTO(
        UUID id,
        UUID userId,
        String userName,
        int delta,
        String reason,
        UUID grantedByUserId,
        String grantedByName,
        OffsetDateTime createdAt
) {}
