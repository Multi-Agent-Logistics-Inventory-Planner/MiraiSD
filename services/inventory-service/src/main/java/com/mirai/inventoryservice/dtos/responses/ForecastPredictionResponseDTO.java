package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ForecastPredictionResponseDTO(
    UUID id,
    UUID itemId,
    String itemName,
    String itemSku,
    Integer currentStock,
    Integer horizonDays,
    BigDecimal avgDailyDelta,
    BigDecimal daysToStockout,
    Integer suggestedReorderQty,
    LocalDate suggestedOrderDate,
    BigDecimal unitCost,
    BigDecimal confidence,
    OffsetDateTime computedAt
) {}
