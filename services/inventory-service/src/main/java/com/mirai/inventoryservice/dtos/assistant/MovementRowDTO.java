package com.mirai.inventoryservice.dtos.assistant;

import com.mirai.inventoryservice.models.enums.StockMovementReason;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MovementRowDTO(
        Long id,
        OffsetDateTime at,
        StockMovementReason reason,
        Integer quantityChange,
        Integer previousQuantity,
        Integer currentQuantity,
        UUID fromLocationId,
        UUID toLocationId
) {}
