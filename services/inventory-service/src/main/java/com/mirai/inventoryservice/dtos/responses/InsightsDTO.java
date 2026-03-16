package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Analytics insights including category performance and day-of-week patterns.
 * Uses demand-based metrics instead of revenue-based metrics.
 */
public record InsightsDTO(
    List<CategoryPerformance> categoryPerformance,
    List<DayOfWeekPattern> dayOfWeekPatterns,
    List<Mover> topMovers,
    List<Mover> bottomMovers,
    PeriodSummary currentPeriod,
    PeriodSummary previousPeriod
) {
    /**
     * Category-level performance metrics using demand-based approach.
     */
    public record CategoryPerformance(
        UUID categoryId,
        String categoryName,
        int totalItems,
        int totalStock,
        int unitsSold,
        BigDecimal avgDemandVelocity,
        BigDecimal totalDemand,
        BigDecimal demandShare,
        BigDecimal avgVolatility,
        BigDecimal avgForecastAccuracy
    ) {}

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
     */
    public record Mover(
        UUID itemId,
        String name,
        String sku,
        String categoryName,
        int currentPeriodUnits,
        int previousPeriodUnits,
        BigDecimal percentChange,
        MoverDirection direction
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
