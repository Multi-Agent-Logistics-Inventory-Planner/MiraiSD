package com.mirai.inventoryservice.dtos.responses;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PredictionDismissalDTO(
        UUID itemId,
        OffsetDateTime dismissedAt,
        UUID dismissedBy,
        OffsetDateTime computedAt,
        String reason
) {}
