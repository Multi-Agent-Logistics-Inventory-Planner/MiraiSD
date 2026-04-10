package com.mirai.inventoryservice.dtos.assistant;

import com.mirai.inventoryservice.models.enums.StockMovementReason;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Aggregate movement summary returned by the {@code movements/summary}
 * endpoint. Eliminates ~80% of the raw-movements calls the LLM would otherwise
 * make for questions like "how many restocks last month?".
 */
public record MovementSummaryDTO(
        Map<StockMovementReason, Long> byReason,
        Map<StockMovementReason, OffsetDateTime> lastByReason,
        BiggestSingleDay biggestSingleDay
) {
    public record BiggestSingleDay(LocalDate date, Long units) {}
}
