package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PredictionDismissalRequest(
        @NotNull UUID itemId,
        OffsetDateTime computedAt,
        String reason
) {}
