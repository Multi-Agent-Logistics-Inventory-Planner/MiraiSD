package com.mirai.inventoryservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.analytics.CategoryDemandRollup;
import com.mirai.inventoryservice.models.analytics.DailySalesRollup;
import com.mirai.inventoryservice.models.analytics.ForecastDailySnapshot;
import com.mirai.inventoryservice.models.analytics.MonthlyPerformanceRollup;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.CategoryDemandRollupRepository;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.DailySalesRollupRepository;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.ForecastSnapshotRepository;
import com.mirai.inventoryservice.repositories.InventoryTotalsRepository;
import com.mirai.inventoryservice.repositories.MonthlyPerformanceRollupRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Service for seeding analytics data in the development environment.
 * Generates comprehensive mock data for testing analytics features.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsSeedService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DailySalesRollupRepository dailySalesRollupRepository;
    private final MonthlyPerformanceRollupRepository monthlyPerformanceRollupRepository;
    private final ForecastPredictionRepository forecastPredictionRepository;
    private final ForecastSnapshotRepository forecastSnapshotRepository;
    private final CategoryDemandRollupRepository categoryDemandRollupRepository;
    private final InventoryTotalsRepository inventoryTotalsRepository;
    private final CacheManager cacheManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    // Day-of-week multipliers: Sunday=0, Monday=1, ..., Saturday=6
    // Higher traffic on weekends (arcade business pattern)
    private static final double[] DOW_MULTIPLIERS = {1.4, 0.7, 0.8, 0.9, 1.0, 1.3, 1.5};

    /**
     * Seed all analytics data including DOW patterns, daily rollups, monthly rollups, and forecasts.
     */
    @Transactional
    public Map<String, Object> seedAllAnalytics(int monthsBack) {
        log.info("Starting analytics seed for {} months back", monthsBack);

        // Load products once and pass to sub-methods to avoid multiple findAll() calls
        List<Product> products = productRepository.findAll();

        // Ensure products have proper defaults before seeding forecasts
        int productsUpdated = updateProductDefaults(products);

        int salesCreated = seedDowPatternSales(monthsBack, products);
        int dailyRollups = seedDailyRollups(monthsBack, products);
        int monthlyRollups = seedMonthlyRollups(monthsBack);
        int forecastsCreated = seedForecastPredictions(products);

        return Map.of(
            "success", true,
            "productsUpdated", productsUpdated,
            "salesCreated", salesCreated,
            "dailyRollupsCreated", dailyRollups,
            "monthlyRollupsCreated", monthlyRollups,
            "forecastsCreated", forecastsCreated
        );
    }

    /**
     * Seed forecast predictions with varied urgency levels for Action Center testing.
     * Creates predictions with different daysToStockout values to simulate:
     * - CRITICAL: daysToStockout < leadTimeDays (3 items)
     * - URGENT: daysToStockout < 2*leadTimeDays (4 items)
     * - ATTENTION: daysToStockout < 3*leadTimeDays (4 items)
     * - HEALTHY: rest of items
     */
    @Transactional
    public int seedForecastPredictions() {
        return seedForecastPredictions(productRepository.findAll());
    }

    private int seedForecastPredictions(List<Product> products) {
        if (products.isEmpty()) {
            log.warn("No products found for forecast seeding");
            return 0;
        }

        // Delete existing forecast predictions to avoid duplicates
        forecastPredictionRepository.deleteAll();

        List<ForecastPrediction> predictions = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = LocalDate.now();

        int criticalCount = 0, urgentCount = 0, attentionCount = 0;
        int maxCritical = 3, maxUrgent = 4, maxAttention = 4;

        for (Product product : products) {
            int leadTime = product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7;
            int reorderPoint = product.getReorderPoint() != null ? product.getReorderPoint() : 10;
            int targetStock = product.getTargetStockLevel() != null ? product.getTargetStockLevel() : 50;

            // Calculate days to stockout and urgency
            BigDecimal daysToStockout;
            int suggestedQty;
            LocalDate suggestedOrderDate;

            // Distribute items across urgency levels
            if (criticalCount < maxCritical) {
                // CRITICAL: Will stockout before reorder arrives
                daysToStockout = BigDecimal.valueOf(leadTime - 2 - random.nextInt(3));
                if (daysToStockout.compareTo(BigDecimal.ZERO) < 0) {
                    daysToStockout = BigDecimal.valueOf(random.nextInt(3) + 1);
                }
                suggestedQty = targetStock + random.nextInt(20);
                suggestedOrderDate = today; // Order immediately
                criticalCount++;
            } else if (urgentCount < maxUrgent) {
                // URGENT: Stockout within 2x lead time
                daysToStockout = BigDecimal.valueOf(leadTime + random.nextInt(leadTime - 1));
                suggestedQty = targetStock - reorderPoint + random.nextInt(10);
                suggestedOrderDate = today.plusDays(random.nextInt(3));
                urgentCount++;
            } else if (attentionCount < maxAttention) {
                // ATTENTION: Stockout within 3x lead time
                daysToStockout = BigDecimal.valueOf(leadTime * 2 + random.nextInt(leadTime - 1));
                suggestedQty = (targetStock - reorderPoint) / 2 + random.nextInt(10);
                suggestedOrderDate = today.plusDays(leadTime / 2);
                attentionCount++;
            } else {
                // HEALTHY: Well-stocked
                daysToStockout = BigDecimal.valueOf(leadTime * 4 + random.nextInt(30));
                suggestedQty = 0;
                suggestedOrderDate = null;
            }

            // Calculate avg daily delta (negative for sales consumption)
            BigDecimal avgDailyDelta = BigDecimal.valueOf(-1 * (random.nextDouble() * 3 + 0.5))
                .setScale(2, RoundingMode.HALF_UP);

            // Confidence varies with urgency (lower for critical items due to higher uncertainty)
            BigDecimal confidence;
            if (criticalCount <= maxCritical && daysToStockout.doubleValue() < leadTime) {
                confidence = BigDecimal.valueOf(0.6 + random.nextDouble() * 0.2);
            } else {
                confidence = BigDecimal.valueOf(0.75 + random.nextDouble() * 0.2);
            }

            predictions.add(ForecastPrediction.builder()
                .itemId(product.getId())
                .horizonDays(30)
                .avgDailyDelta(avgDailyDelta)
                .daysToStockout(daysToStockout)
                .suggestedReorderQty(suggestedQty)
                .suggestedOrderDate(suggestedOrderDate)
                .confidence(confidence.setScale(2, RoundingMode.HALF_UP))
                .computedAt(now)
                .features(Map.of(
                    "mu_hat", random.nextDouble() * 5 + 1,
                    "sigma_d_hat", random.nextDouble() * 2,
                    "safety_stock", reorderPoint / 2,
                    "reorder_point", reorderPoint,
                    "source", "analytics_seed"
                ))
                .build());
        }

        forecastPredictionRepository.saveAll(predictions);
        log.info("Seeded {} forecast predictions (critical={}, urgent={}, attention={})",
            predictions.size(), criticalCount, urgentCount, attentionCount);
        return predictions.size();
    }

    /**
     * Seed sales with realistic day-of-week patterns.
     */
    @Transactional
    public int seedDowPatternSales(int monthsBack) {
        return seedDowPatternSales(monthsBack, productRepository.findAll());
    }

    private int seedDowPatternSales(int monthsBack, List<Product> products) {
        if (products.isEmpty()) {
            log.warn("No products found for DOW pattern seeding");
            return 0;
        }

        // Build index map once to avoid N+1 in getBaseRateForProduct
        Map<UUID, Integer> productIndexMap = new HashMap<>();
        for (int i = 0; i < products.size(); i++) {
            productIndexMap.put(products.get(i).getId(), i);
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(monthsBack);
        List<StockMovement> movements = new ArrayList<>();

        for (Product product : products) {
            // Vary base sales rate per product (simulating top/steady/slow sellers)
            double baseDailyRate = getBaseRateForProduct(product, products.size(), productIndexMap);

            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(today)) {
                int dow = currentDate.getDayOfWeek().getValue() % 7; // Sunday=0
                double multiplier = DOW_MULTIPLIERS[dow];

                // Add some randomness
                double dailyRate = baseDailyRate * multiplier * (0.7 + random.nextDouble() * 0.6);
                int salesCount = (int) Math.round(dailyRate);

                // Generate sales movements for this day
                for (int i = 0; i < salesCount; i++) {
                    int qty = 1 + random.nextInt(3);
                    int hour = 10 + random.nextInt(12); // 10 AM to 10 PM
                    OffsetDateTime saleTime = currentDate.atTime(hour, random.nextInt(60))
                        .atOffset(ZoneOffset.UTC);

                    movements.add(StockMovement.builder()
                        .locationType(com.mirai.inventoryservice.models.enums.LocationType.BOX_BIN)
                        .item(product)
                        .quantityChange(-qty)
                        .previousQuantity(qty + 10)
                        .currentQuantity(10)
                        .reason(StockMovementReason.SALE)
                        .at(saleTime)
                        .metadata(Map.of("source", "analytics_seed", "dow_pattern", true))
                        .build());
                }

                currentDate = currentDate.plusDays(1);
            }
        }

        stockMovementRepository.saveAll(movements);
        log.info("Seeded {} DOW-patterned sales movements", movements.size());
        return movements.size();
    }

    /**
     * Seed daily rollups by aggregating stock movements.
     */
    @Transactional
    public int seedDailyRollups(int monthsBack) {
        return seedDailyRollups(monthsBack, productRepository.findAll());
    }

    private int seedDailyRollups(int monthsBack, List<Product> products) {
        if (products.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(monthsBack);

        // Get all sales movements in the date range
        OffsetDateTime startDateTime = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<StockMovement> movements = stockMovementRepository.findByReasonAndAtAfterWithItem(
            StockMovementReason.SALE, startDateTime);

        // Aggregate by item and date
        Map<String, DailySalesRollup> rollupMap = new HashMap<>();

        for (StockMovement movement : movements) {
            LocalDate date = movement.getAt().toLocalDate();
            UUID itemId = movement.getItem().getId();
            String key = itemId.toString() + "_" + date.toString();

            DailySalesRollup rollup = rollupMap.computeIfAbsent(key, k ->
                DailySalesRollup.builder()
                    .itemId(itemId)
                    .rollupDate(date)
                    .unitsSold(0)
                    .revenue(BigDecimal.ZERO)
                    .cost(BigDecimal.ZERO)
                    .profit(BigDecimal.ZERO)
                    .restockUnits(0)
                    .damageUnits(0)
                    .movementCount(0)
                    .build()
            );

            // Calculate revenue (MSRP-based), cost, and profit
            Product item = movement.getItem();
            int units = Math.abs(movement.getQuantityChange());
            BigDecimal unitQty = BigDecimal.valueOf(units);
            BigDecimal msrp = item.getMsrp() != null ? item.getMsrp() : BigDecimal.ZERO;
            BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;

            BigDecimal revenue = msrp.multiply(unitQty);
            BigDecimal cost = unitCost.multiply(unitQty);
            BigDecimal profit = revenue.subtract(cost);

            rollup.setUnitsSold(rollup.getUnitsSold() + units);
            rollup.setRevenue(rollup.getRevenue().add(revenue));
            rollup.setCost(rollup.getCost().add(cost));
            rollup.setProfit(rollup.getProfit().add(profit));
            rollup.setMovementCount(rollup.getMovementCount() + 1);
        }

        // Also process restock and damage movements
        List<StockMovement> restocks = stockMovementRepository.findByReasonAndAtAfterWithItem(
            StockMovementReason.RESTOCK, startDateTime);
        for (StockMovement movement : restocks) {
            LocalDate date = movement.getAt().toLocalDate();
            UUID itemId = movement.getItem().getId();
            String key = itemId.toString() + "_" + date.toString();

            DailySalesRollup rollup = rollupMap.get(key);
            if (rollup != null) {
                rollup.setRestockUnits(rollup.getRestockUnits() + Math.abs(movement.getQuantityChange()));
                rollup.setMovementCount(rollup.getMovementCount() + 1);
            }
        }

        List<DailySalesRollup> rollups = new ArrayList<>(rollupMap.values());

        // Delete existing rollups in the date range to avoid duplicates
        dailySalesRollupRepository.deleteAll(
            dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(startDate, today));

        dailySalesRollupRepository.saveAll(rollups);
        log.info("Seeded {} daily rollups", rollups.size());
        return rollups.size();
    }

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

    /**
     * Seed monthly rollups by aggregating daily rollups.
     */
    @Transactional
    public int seedMonthlyRollups(int monthsBack) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(monthsBack);

        List<DailySalesRollup> dailyRollups = dailySalesRollupRepository
            .findByRollupDateBetweenOrderByRollupDateAsc(startDate, today);

        if (dailyRollups.isEmpty()) {
            return 0;
        }

        // Get product to category mapping
        Map<UUID, UUID> productCategoryMap = new HashMap<>();
        productRepository.findAllWithCategories().forEach(p -> {
            if (p.getCategory() != null) {
                productCategoryMap.put(p.getId(), p.getCategory().getId());
            }
        });

        // Aggregate by category and month
        Map<String, MonthlyPerformanceRollup> rollupMap = new HashMap<>();

        for (DailySalesRollup daily : dailyRollups) {
            UUID categoryId = productCategoryMap.get(daily.getItemId());
            if (categoryId == null) continue;

            int year = daily.getRollupDate().getYear();
            int month = daily.getRollupDate().getMonthValue();
            String key = categoryId.toString() + "_" + year + "_" + month;

            MonthlyPerformanceRollup rollup = rollupMap.computeIfAbsent(key, k ->
                MonthlyPerformanceRollup.builder()
                    .categoryId(categoryId)
                    .rollupYear(year)
                    .rollupMonth(month)
                    .totalUnitsSold(0)
                    .totalRevenue(BigDecimal.ZERO)
                    .totalRestockUnits(0)
                    .totalDamageUnits(0)
                    .uniqueItemsSold(0)
                    .build()
            );

            rollup.setTotalUnitsSold(rollup.getTotalUnitsSold() + daily.getUnitsSold());
            rollup.setTotalRevenue(rollup.getTotalRevenue().add(daily.getRevenue()));
            rollup.setTotalRestockUnits(rollup.getTotalRestockUnits() + daily.getRestockUnits());
            rollup.setTotalDamageUnits(rollup.getTotalDamageUnits() + daily.getDamageUnits());
        }

        // Count unique items per category-month
        Map<String, java.util.Set<UUID>> uniqueItemsMap = new HashMap<>();
        for (DailySalesRollup daily : dailyRollups) {
            UUID categoryId = productCategoryMap.get(daily.getItemId());
            if (categoryId == null) continue;

            int year = daily.getRollupDate().getYear();
            int month = daily.getRollupDate().getMonthValue();
            String key = categoryId.toString() + "_" + year + "_" + month;

            uniqueItemsMap.computeIfAbsent(key, k -> new java.util.HashSet<>())
                .add(daily.getItemId());
        }

        for (Map.Entry<String, MonthlyPerformanceRollup> entry : rollupMap.entrySet()) {
            java.util.Set<UUID> items = uniqueItemsMap.get(entry.getKey());
            if (items != null) {
                entry.getValue().setUniqueItemsSold(items.size());
            }
        }

        List<MonthlyPerformanceRollup> rollups = new ArrayList<>(rollupMap.values());

        // Clear existing monthly rollups
        int startYear = startDate.getYear();
        int startMonth = startDate.getMonthValue();
        monthlyPerformanceRollupRepository.deleteOlderThan(startYear, startMonth);
        monthlyPerformanceRollupRepository.deleteAll(
            monthlyPerformanceRollupRepository.findByYearOnwards(startYear));

        monthlyPerformanceRollupRepository.saveAll(rollups);
        log.info("Seeded {} monthly rollups", rollups.size());
        return rollups.size();
    }

    /**
     * Clear all analytics seed data.
     */
    @Transactional
    public Map<String, Object> clearAnalyticsSeedData() {
        // Clear analytics-seeded stock movements
        List<StockMovement> seedMovements = stockMovementRepository.findAll().stream()
            .filter(m -> m.getMetadata() != null && "analytics_seed".equals(m.getMetadata().get("source")))
            .toList();
        stockMovementRepository.deleteAll(seedMovements);

        // Clear all daily rollups
        long dailyCount = dailySalesRollupRepository.count();
        dailySalesRollupRepository.deleteAll();

        // Clear all monthly rollups
        long monthlyCount = monthlyPerformanceRollupRepository.count();
        monthlyPerformanceRollupRepository.deleteAll();

        // Clear analytics-seeded forecasts
        long forecastCount = forecastPredictionRepository.count();
        forecastPredictionRepository.deleteAll();

        log.info("Cleared analytics seed data: {} sales, {} daily rollups, {} monthly rollups, {} forecasts",
            seedMovements.size(), dailyCount, monthlyCount, forecastCount);

        return Map.of(
            "success", true,
            "salesDeleted", seedMovements.size(),
            "dailyRollupsDeleted", dailyCount,
            "monthlyRollupsDeleted", monthlyCount,
            "forecastsDeleted", forecastCount
        );
    }

    /**
     * Ensure all products have proper lead time and reorder point values.
     * Called before seeding forecasts to ensure all products have the required fields.
     */
    @Transactional
    public int updateProductDefaults() {
        return updateProductDefaults(productRepository.findAll());
    }

    private int updateProductDefaults(List<Product> products) {
        int updated = 0;

        for (Product product : products) {
            boolean needsUpdate = false;

            if (product.getLeadTimeDays() == null) {
                product.setLeadTimeDays(5 + random.nextInt(10)); // 5-14 days
                needsUpdate = true;
            }
            if (product.getReorderPoint() == null) {
                product.setReorderPoint(5 + random.nextInt(15)); // 5-19 units
                needsUpdate = true;
            }
            if (product.getTargetStockLevel() == null) {
                int reorderPoint = product.getReorderPoint();
                product.setTargetStockLevel(reorderPoint * 3 + random.nextInt(20)); // ~3x reorder point
                needsUpdate = true;
            }

            if (needsUpdate) {
                updated++;
            }
        }

        if (updated > 0) {
            productRepository.saveAll(products);
            log.info("Updated {} products with default lead times and reorder points", updated);
        }

        return updated;
    }

    /**
     * Compute rollups from existing stock movements (no seeding of new movements).
     * Used by the scheduled job.
     */
    @Transactional
    public void computeRollupsFromExistingData(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime startDateTime = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTime = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        // Get sales movements
        List<StockMovement> movements = stockMovementRepository.findByReasonAndAtBetweenWithItem(
            StockMovementReason.SALE, startDateTime, endDateTime);

        // Aggregate by item and date
        Map<String, DailySalesRollup> rollupMap = new HashMap<>();

        for (StockMovement movement : movements) {
            LocalDate date = movement.getAt().toLocalDate();
            UUID itemId = movement.getItem().getId();
            String key = itemId.toString() + "_" + date.toString();

            DailySalesRollup existing = dailySalesRollupRepository
                .findByItemIdAndRollupDate(itemId, date)
                .orElse(null);

            DailySalesRollup rollup = rollupMap.computeIfAbsent(key, k ->
                existing != null ? existing : DailySalesRollup.builder()
                    .itemId(itemId)
                    .rollupDate(date)
                    .unitsSold(0)
                    .revenue(BigDecimal.ZERO)
                    .cost(BigDecimal.ZERO)
                    .profit(BigDecimal.ZERO)
                    .restockUnits(0)
                    .damageUnits(0)
                    .movementCount(0)
                    .build()
            );

            if (existing == null) {
                // Calculate revenue (MSRP-based), cost, and profit
                Product item = movement.getItem();
                int units = Math.abs(movement.getQuantityChange());
                BigDecimal unitQty = BigDecimal.valueOf(units);
                BigDecimal msrp = item.getMsrp() != null ? item.getMsrp() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;

                BigDecimal revenue = msrp.multiply(unitQty);
                BigDecimal cost = unitCost.multiply(unitQty);
                BigDecimal profit = revenue.subtract(cost);

                rollup.setUnitsSold(rollup.getUnitsSold() + units);
                rollup.setRevenue(rollup.getRevenue().add(revenue));
                rollup.setCost(rollup.getCost().add(cost));
                rollup.setProfit(rollup.getProfit().add(profit));
                rollup.setMovementCount(rollup.getMovementCount() + 1);
            }
        }

        dailySalesRollupRepository.saveAll(rollupMap.values());
    }

    private double getBaseRateForProduct(Product product, int totalProducts, Map<UUID, Integer> productIndexMap) {
        // Assign products to tiers based on their position
        int index = productIndexMap.getOrDefault(product.getId(), 0);
        double position = (double) index / totalProducts;

        if (position < 0.2) {
            // Top sellers: 3-6 sales per day
            return 3 + random.nextDouble() * 3;
        } else if (position < 0.5) {
            // Steady performers: 1-3 sales per day
            return 1 + random.nextDouble() * 2;
        } else if (position < 0.8) {
            // Slow movers: 0.2-1 sales per day
            return 0.2 + random.nextDouble() * 0.8;
        } else {
            // New arrivals / rare items: 0-0.5 sales per day
            return random.nextDouble() * 0.5;
        }
    }

    /**
     * Snapshot forecast data for all items.
     * Captures mu_hat, sigma_d_hat, confidence, mape from forecast_predictions features.
     * Called by scheduled job at 2:00 AM daily.
     */
    @Transactional
    public int snapshotForecastData() {
        LocalDate today = LocalDate.now();
        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();

        if (latestPredictions.isEmpty()) {
            log.warn("No forecast predictions found for snapshot");
            return 0;
        }

        // Batch fetch existing snapshots for today to avoid N+1 EXISTS queries
        java.util.Set<UUID> existingItemIds = new java.util.HashSet<>(
            forecastSnapshotRepository.findItemIdsBySnapshotDate(today));

        List<ForecastDailySnapshot> snapshots = new ArrayList<>();

        for (ForecastPrediction prediction : latestPredictions) {
            // Skip if already snapshotted today
            if (existingItemIds.contains(prediction.getItemId())) {
                continue;
            }

            Map<String, Object> features = prediction.getFeatures();
            if (features == null) {
                features = new HashMap<>();
            }

            BigDecimal muHat = extractBigDecimal(features.get("mu_hat"));
            BigDecimal sigmaDHat = extractBigDecimal(features.get("sigma_d_hat"));
            BigDecimal mape = extractBigDecimal(features.get("mape"));
            String dowMultipliers = extractDowMultipliers(features.get("dow_multipliers"));

            snapshots.add(ForecastDailySnapshot.builder()
                .itemId(prediction.getItemId())
                .snapshotDate(today)
                .muHat(muHat)
                .sigmaDHat(sigmaDHat)
                .confidence(prediction.getConfidence())
                .mape(mape)
                .daysToStockout(prediction.getDaysToStockout())
                .currentStock(stockMap.getOrDefault(prediction.getItemId(), 0))
                .dowMultipliers(dowMultipliers)
                .build());
        }

        if (!snapshots.isEmpty()) {
            forecastSnapshotRepository.saveAll(snapshots);
            log.info("Created {} forecast snapshots for {}", snapshots.size(), today);
        }

        return snapshots.size();
    }

    /**
     * Rollup category demand metrics.
     * Aggregates demand velocity, stock velocity, and risk counts by category.
     * Called by scheduled job at 2:15 AM daily.
     */
    @Transactional
    public int rollupCategoryDemand() {
        LocalDate today = LocalDate.now();
        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();
        List<Product> products = productRepository.findAllWithCategories();
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();

        if (products.isEmpty() || latestPredictions.isEmpty()) {
            log.warn("No products or predictions found for category demand rollup");
            return 0;
        }

        // Build prediction map by item ID
        Map<UUID, ForecastPrediction> predictionMap = new HashMap<>();
        for (ForecastPrediction p : latestPredictions) {
            predictionMap.put(p.getItemId(), p);
        }

        // Group products by category
        Map<UUID, List<Product>> productsByCategory = new HashMap<>();
        for (Product product : products) {
            if (product.getCategory() != null && product.getIsActive()) {
                productsByCategory
                    .computeIfAbsent(product.getCategory().getId(), k -> new ArrayList<>())
                    .add(product);
            }
        }

        // Batch fetch existing rollups for today to avoid N+1 EXISTS queries
        java.util.Set<UUID> existingCategoryIds = new java.util.HashSet<>(
            categoryDemandRollupRepository.findCategoryIdsByRollupDate(today));

        List<CategoryDemandRollup> rollups = new ArrayList<>();

        for (Map.Entry<UUID, List<Product>> entry : productsByCategory.entrySet()) {
            UUID categoryId = entry.getKey();
            List<Product> categoryProducts = entry.getValue();

            // Skip if already rolled up today
            if (existingCategoryIds.contains(categoryId)) {
                continue;
            }

            BigDecimal totalDemandVelocity = BigDecimal.ZERO;
            BigDecimal totalConfidence = BigDecimal.ZERO;
            BigDecimal totalVolatility = BigDecimal.ZERO;
            int totalStock = 0;
            int totalUnitsSold = 0;
            int itemsAtRisk = 0;
            int itemsCritical = 0;
            int itemsHealthy = 0;
            int activeCount = 0;

            for (Product product : categoryProducts) {
                ForecastPrediction prediction = predictionMap.get(product.getId());
                int stock = stockMap.getOrDefault(product.getId(), 0);
                totalStock += stock;

                if (prediction != null) {
                    Map<String, Object> features = prediction.getFeatures() != null
                        ? prediction.getFeatures() : new HashMap<>();
                    BigDecimal muHat = extractBigDecimal(features.get("mu_hat"));
                    BigDecimal sigmaDHat = extractBigDecimal(features.get("sigma_d_hat"));

                    if (muHat != null) {
                        totalDemandVelocity = totalDemandVelocity.add(muHat);
                        activeCount++;

                        // Calculate volatility (CV = sigma / mu)
                        if (muHat.compareTo(BigDecimal.ZERO) > 0 && sigmaDHat != null) {
                            BigDecimal cv = sigmaDHat.divide(muHat, 4, RoundingMode.HALF_UP);
                            totalVolatility = totalVolatility.add(cv);
                        }
                    }

                    if (prediction.getConfidence() != null) {
                        totalConfidence = totalConfidence.add(prediction.getConfidence());
                    }

                    // Determine urgency level
                    int leadTime = product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7;
                    BigDecimal daysToStockout = prediction.getDaysToStockout();
                    if (daysToStockout != null) {
                        double days = daysToStockout.doubleValue();
                        if (days < leadTime || days < leadTime * 2) {
                            itemsCritical++;
                        } else if (days < leadTime * 3) {
                            itemsAtRisk++;
                        } else {
                            itemsHealthy++;
                        }
                    } else {
                        itemsHealthy++;
                    }
                } else {
                    itemsHealthy++;
                }
            }

            BigDecimal avgDemandVelocity = activeCount > 0
                ? totalDemandVelocity.divide(BigDecimal.valueOf(activeCount), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            BigDecimal avgConfidence = activeCount > 0
                ? totalConfidence.divide(BigDecimal.valueOf(activeCount), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            BigDecimal avgVolatility = activeCount > 0
                ? totalVolatility.divide(BigDecimal.valueOf(activeCount), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            BigDecimal avgStockVelocity = totalStock > 0 && totalDemandVelocity.compareTo(BigDecimal.ZERO) > 0
                ? totalDemandVelocity.divide(BigDecimal.valueOf(totalStock), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            rollups.add(CategoryDemandRollup.builder()
                .categoryId(categoryId)
                .rollupDate(today)
                .totalDemandVelocity(totalDemandVelocity)
                .avgDemandVelocity(avgDemandVelocity)
                .totalUnitsSold(totalUnitsSold)
                .totalStock(totalStock)
                .avgStockVelocity(avgStockVelocity)
                .itemsAtRisk(itemsAtRisk)
                .itemsCritical(itemsCritical)
                .itemsHealthy(itemsHealthy)
                .avgConfidence(avgConfidence)
                .avgVolatility(avgVolatility)
                .activeItemCount(activeCount)
                .build());
        }

        if (!rollups.isEmpty()) {
            categoryDemandRollupRepository.saveAll(rollups);
            log.info("Created {} category demand rollups for {}", rollups.size(), today);
        }

        return rollups.size();
    }

    /**
     * Extract BigDecimal from features map value.
     */
    private BigDecimal extractBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue())
                .setScale(4, RoundingMode.HALF_UP);
        }
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value).setScale(4, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract dow_multipliers as JSON string from features map.
     */
    private String extractDowMultipliers(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
