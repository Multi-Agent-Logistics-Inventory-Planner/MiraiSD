package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Demand leaders analysis for admin inventory prioritization.
 * Uses demand-based metrics (mu_hat, volatility, forecast accuracy)
 * instead of revenue-based metrics.
 */
public record DemandLeadersDTO(
    List<DemandLeader> byDemandVelocity,
    List<DemandLeader> byStockVelocity,
    List<CategoryRanking> categoryRankings,
    DemandSummary summary
) {
    /**
     * Individual high-demand product.
     */
    public record DemandLeader(
        int rank,
        UUID itemId,
        String name,
        String sku,
        String imageUrl,
        String categoryName,
        int periodDemand,
        BigDecimal demandVelocity,
        BigDecimal demandVolatility,
        BigDecimal forecastAccuracy,
        BigDecimal stockVelocity,
        BigDecimal percentOfTotal
    ) {}

    /**
     * Category ranking by total demand velocity.
     */
    public record CategoryRanking(
        int rank,
        UUID categoryId,
        String categoryName,
        int totalItems,
        int periodDemand,
        BigDecimal totalDemandVelocity,
        BigDecimal percentOfTotal
    ) {}

    /**
     * Overall demand summary.
     */
    public record DemandSummary(
        BigDecimal totalDemandVelocity,
        int totalPeriodDemand,
        int uniqueItemsWithDemand,
        BigDecimal demandGrowthPercent,
        BigDecimal systemForecastAccuracy,
        String periodLabel
    ) {}
}
