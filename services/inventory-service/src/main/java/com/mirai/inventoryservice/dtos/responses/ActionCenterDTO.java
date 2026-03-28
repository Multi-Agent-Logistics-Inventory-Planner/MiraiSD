package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consolidated action center data for reorder decisions.
 * Replaces 10+ separate queries with a single endpoint.
 * Uses demand-based metrics instead of cost-based metrics.
 */
public record ActionCenterDTO(
    List<ActionItem> items,
    int totalActionItems,
    BigDecimal avgForecastAccuracy,
    BigDecimal totalDemandVelocity,
    RiskSummary riskSummary
) {
    /**
     * Single item requiring action (reorder decision).
     * Pre-sorted by urgency on server side.
     * Uses demand velocity instead of cost for prioritization.
     */
    public record ActionItem(
        UUID itemId,
        String name,
        String sku,
        String imageUrl,
        String categoryName,
        int currentStock,
        int reorderPoint,
        int targetStockLevel,
        BigDecimal daysToStockout,
        BigDecimal avgDailyDelta,
        int suggestedReorderQty,
        LocalDate suggestedOrderDate,
        Integer leadTimeDays,
        BigDecimal demandVelocity,
        BigDecimal demandVolatility,
        BigDecimal forecastAccuracy,
        BigDecimal confidence,
        ActionUrgency urgency,
        boolean overdue,
        String computedAt
    ) {}

    /**
     * Summary counts by urgency level.
     */
    public record RiskSummary(
        int critical,   // stockout imminent (< lead time)
        int urgent,     // needs attention soon (< 2x lead time)
        int attention,  // monitor closely
        int healthy     // ok for now
    ) {
        public int total() {
            return critical + urgent + attention + healthy;
        }
    }

    /**
     * Urgency levels for action items.
     * CRITICAL: days_to_stockout < lead_time_days (will stockout before reorder arrives)
     * URGENT: days_to_stockout < 2 * lead_time_days
     * ATTENTION: days_to_stockout < 3 * lead_time_days OR stock < reorder_point
     * HEALTHY: adequately stocked
     */
    public enum ActionUrgency {
        CRITICAL,
        URGENT,
        ATTENTION,
        HEALTHY
    }
}
