package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Analytics insights including category performance and day-of-week patterns.
 * Uses demand-based metrics instead of revenue-based metrics.
 */
public record InsightsDTO(
    List<DayOfWeekPattern> dayOfWeekPatterns,
    List<Mover> topMovers,
    List<Mover> bottomMovers,
    PeriodSummary currentPeriod,
    PeriodSummary previousPeriod
) {
    /**
     * Day-of-week sales pattern with forecast comparison.
     * Index 0 = Sunday, 6 = Saturday (Postgres EXTRACT DOW convention).
     */
    public record DayOfWeekPattern(
        int dayOfWeek,
        String dayName,
        int totalUnits,
        BigDecimal avgDemandMultiplier,
        BigDecimal percentOfWeeklyTotal
    ) {}

    /**
     * Product showing significant demand change.
     * Ranked by ACV-weighted velocity (demandVelocity × displayConfidence).
     */
    public record Mover(
        int rank,
        UUID itemId,
        String name,
        String imageUrl,
        String categoryName,
        int currentPeriodUnits,
        int previousPeriodUnits,
        BigDecimal percentChange,
        MoverDirection direction,
        BigDecimal demandVelocity,
        BigDecimal displayConfidence
    ) {}

    public enum MoverDirection {
        UP, DOWN, STABLE
    }

    /**
     * Summary metrics for a time period using demand-based approach.
     */
    public record PeriodSummary(
        String periodLabel,
        int totalUnits,
        BigDecimal avgDemandVelocity,
        BigDecimal avgForecastAccuracy,
        int uniqueItemsSold,
        int totalMovements
    ) {}
}
