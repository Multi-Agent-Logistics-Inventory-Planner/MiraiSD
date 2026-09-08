package com.mirai.inventoryservice.analytics.application;

import com.mirai.inventoryservice.config.CacheConfig;
import com.mirai.inventoryservice.models.analytics.DailySalesRollup;
import com.mirai.inventoryservice.repositories.DailySalesRollupRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recomputes daily sales rollups from stock-movement data, reached by
 * {@code AnalyticsController}'s production {@code /recompute-rollups} endpoint. Split out of
 * {@code AnalyticsSeedService} (see .specs/phase-5b-catalog-facade/log.md, T-5 review) because
 * this class's only production-reachable path never depended on catalog data -- it aggregates
 * purely over stock movements -- unlike the rest of {@code AnalyticsSeedService}, which is
 * dev-seeding code exempted from the outside-catalog-to-catalog.infrastructure ArchUnit rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesRollupRecomputeService {

    private final DailySalesRollupRepository dailySalesRollupRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CacheManager cacheManager;

    /**
     * Recompute all daily rollups from existing stock movements.
     * Deletes existing rollups and recreates them with updated MSRP-based revenue calculation.
     * Clears the sales summary cache after completion.
     *
     * OPTIMIZED: Uses bulk delete and SQL aggregation instead of loading entities into memory.
     *
     * @param monthsBack Number of months to recompute
     * @return Number of rollups created
     */
    @Transactional
    public int recomputeAllRollups(int monthsBack) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(monthsBack);

        // Bulk delete existing rollups (single DELETE statement, not entity-by-entity)
        int deleted = dailySalesRollupRepository.deleteByRollupDateBetween(startDate, today);
        log.info("Deleted {} existing rollups for recomputation", deleted);

        // Recompute from stock_movements using SQL aggregation
        int count = recomputeRollupsOptimized(startDate, today);

        // Clear sales summary cache
        var cache = cacheManager.getCache(CacheConfig.SALES_SUMMARY_CACHE);
        if (cache != null) {
            cache.clear();
            log.info("Cleared sales summary cache");
        }

        log.info("Recomputed {} daily rollups", count);
        return count;
    }

    /**
     * Optimized rollup computation using SQL aggregation.
     * Aggregates data in the database instead of loading all movements into memory.
     */
    private int recomputeRollupsOptimized(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime startDateTime = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTime = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // Use native SQL aggregation - much faster than loading all entities
        List<Object[]> aggregatedData = stockMovementRepository.aggregateSalesByItemAndDate(
            startDateTime, endDateTime);

        if (aggregatedData.isEmpty()) {
            log.info("No sales movements found in date range");
            return 0;
        }

        List<DailySalesRollup> rollups = new ArrayList<>();
        for (Object[] row : aggregatedData) {
            UUID itemId = (UUID) row[0];
            LocalDate rollupDate = ((java.sql.Date) row[1]).toLocalDate();
            int unitsSold = ((Number) row[2]).intValue();
            BigDecimal revenue = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            BigDecimal cost = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
            BigDecimal profit = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
            int movementCount = ((Number) row[6]).intValue();

            rollups.add(DailySalesRollup.builder()
                .itemId(itemId)
                .rollupDate(rollupDate)
                .unitsSold(unitsSold)
                .revenue(revenue)
                .cost(cost)
                .profit(profit)
                .restockUnits(0)
                .damageUnits(0)
                .movementCount(movementCount)
                .build());
        }

        // Batch save all rollups
        dailySalesRollupRepository.saveAll(rollups);
        log.info("Created {} daily rollups from SQL aggregation", rollups.size());
        return rollups.size();
    }
}
