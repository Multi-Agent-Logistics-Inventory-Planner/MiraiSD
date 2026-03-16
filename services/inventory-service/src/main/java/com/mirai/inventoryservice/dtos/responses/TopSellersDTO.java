package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Top sellers analysis for admin revenue tracking.
 */
public record TopSellersDTO(
    List<TopSeller> byRevenue,
    List<TopSeller> byUnits,
    List<CategoryRanking> categoryRankings,
    RevenueSummary summary
) {
    /**
     * Individual top-selling product.
     */
    public record TopSeller(
        int rank,
        UUID itemId,
        String name,
        String sku,
        String imageUrl,
        String categoryName,
        int unitsSold,
        BigDecimal revenue,
        BigDecimal percentOfTotal,
        BigDecimal avgUnitPrice
    ) {}

    /**
     * Category ranking by total revenue.
     */
    public record CategoryRanking(
        int rank,
        UUID categoryId,
        String categoryName,
        int totalItems,
        int unitsSold,
        BigDecimal revenue,
        BigDecimal percentOfTotal
    ) {}

    /**
     * Overall revenue summary.
     */
    public record RevenueSummary(
        BigDecimal totalRevenue,
        int totalUnitsSold,
        int uniqueItemsSold,
        BigDecimal avgOrderValue,
        BigDecimal revenueChangePercent,
        String periodLabel
    ) {}
}
