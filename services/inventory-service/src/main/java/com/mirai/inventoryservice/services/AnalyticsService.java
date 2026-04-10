package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ActionCenterDTO;
import com.mirai.inventoryservice.dtos.responses.ActionCenterDTO.ActionItem;
import com.mirai.inventoryservice.dtos.responses.ActionCenterDTO.ActionUrgency;
import com.mirai.inventoryservice.dtos.responses.ActionCenterDTO.RiskSummary;
import com.mirai.inventoryservice.dtos.responses.CategoryInventoryDTO;
import com.mirai.inventoryservice.dtos.responses.DailySalesDTO;
import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO;
import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO.CategoryRanking;
import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO.DemandLeader;
import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO.DemandSummary;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO.DayOfWeekPattern;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO.Mover;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO.MoverDirection;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO.PeriodSummary;
import com.mirai.inventoryservice.dtos.responses.MonthlySalesDTO;
import com.mirai.inventoryservice.dtos.responses.PerformanceMetricsDTO;
import com.mirai.inventoryservice.dtos.responses.SalesSummaryDTO;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.analytics.DailySalesRollup;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.DailySalesRollupRepository;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.InventoryTotalsRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ForecastPredictionRepository forecastPredictionRepository;
    private final InventoryTotalsRepository inventoryTotalsRepository;
    private final DailySalesRollupRepository dailySalesRollupRepository;
    private final MachineDisplayRepository machineDisplayRepository;

    private static final String[] DOW_NAMES = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final int MOVERS_LIMIT = 10;
    private static final double MIN_DISPLAY_DAYS = 3.0;
    private static final double PERIOD_DAYS = 30.0;
    private static final int CATEGORY_RANKINGS_LIMIT = 5;
    private static final BigDecimal MU_HAT_EPSILON = new BigDecimal("0.001");
    private static final BigDecimal MAX_DAYS = new BigDecimal("365");

    /**
     * Helper record for extracted forecast features.
     */
    private record ForecastFeatures(
        BigDecimal muHat,
        BigDecimal sigmaDHat,
        BigDecimal mape,
        List<Double> dowMultipliers,
        Integer safetyStock
    ) {}

    /**
     * Extract features from ForecastPrediction JSONB.
     */
    private ForecastFeatures extractFeatures(ForecastPrediction fp) {
        Map<String, Object> features = fp.getFeatures();
        if (features == null) {
            return new ForecastFeatures(null, null, null, null, null);
        }

        BigDecimal muHat = extractBigDecimal(features.get("mu_hat"));
        BigDecimal sigmaDHat = extractBigDecimal(features.get("sigma_d_hat"));
        BigDecimal mape = extractBigDecimal(features.get("mape"));

        List<Double> dowMultipliers = null;
        Object dowObj = features.get("dow_multipliers");
        if (dowObj instanceof List<?> list) {
            dowMultipliers = list.stream()
                .filter(o -> o instanceof Number)
                .map(o -> ((Number) o).doubleValue())
                .toList();
        }

        Integer safetyStock = null;
        Object ssObj = features.get("safety_stock");
        if (ssObj instanceof Number num) {
            safetyStock = num.intValue();
        }

        return new ForecastFeatures(muHat, sigmaDHat, mape, dowMultipliers, safetyStock);
    }

    private BigDecimal extractBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        }
        if (value instanceof String s) {
            try {
                return new BigDecimal(s).setScale(4, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Calculate forecast accuracy from MAPE: (1 - mape) * 100
     */
    private BigDecimal calculateForecastAccuracy(BigDecimal mape) {
        if (mape == null) return null;
        return BigDecimal.ONE.subtract(mape)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Calculate demand volatility (coefficient of variation): sigma_d_hat / mu_hat
     */
    private BigDecimal calculateDemandVolatility(BigDecimal muHat, BigDecimal sigmaDHat) {
        if (muHat == null || sigmaDHat == null || muHat.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return sigmaDHat.divide(muHat, 4, RoundingMode.HALF_UP);
    }

    /**
     * Assign sequential ranks to a list of items using a mapper function.
     * @param items The list of items to rank
     * @param rankMapper Function that takes (1-based rank, item) and returns a new ranked item
     * @return A new list with ranks assigned
     */
    private <T, R> List<R> assignRanks(List<T> items, BiFunction<Integer, T, R> rankMapper) {
        List<R> ranked = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ranked.add(rankMapper.apply(i + 1, items.get(i)));
        }
        return ranked;
    }

    /**
     * Recalculate days to stockout using live stock and stored mu_hat.
     * Returns null if mu_hat is missing or negligible.
     */
    private BigDecimal recalculateDaysToStockout(int currentStock, BigDecimal muHat) {
        if (currentStock <= 0) {
            return BigDecimal.ZERO;
        }
        if (muHat == null || muHat.compareTo(MU_HAT_EPSILON) <= 0) {
            return null;
        }
        BigDecimal result = BigDecimal.valueOf(currentStock)
            .divide(muHat, 2, RoundingMode.HALF_UP);
        if (result.compareTo(MAX_DAYS) > 0) {
            return MAX_DAYS;
        }
        return result;
    }

    /**
     * Recalculate suggested order date from fresh days-to-stockout and lead time.
     * Returns null if daysToStockout is null (infinite horizon).
     */
    private LocalDate recalculateSuggestedOrderDate(BigDecimal daysToStockout, int leadTimeDays, LocalDate today) {
        if (daysToStockout == null) {
            return null;
        }
        long daysUntilStockout = daysToStockout.setScale(0, RoundingMode.CEILING).longValue();
        if (daysUntilStockout <= leadTimeDays) {
            return today;
        }
        return today.plusDays(daysUntilStockout - leadTimeDays);
    }

    /**
     * Recalculate suggested reorder quantity using forecast model data.
     * Formula: (targetStock - currentStock) + leadTimeDemand + safetyStock
     *
     * @param currentStock Current inventory level
     * @param targetStockLevel Desired stock level after reorder
     * @param muHat Demand velocity from forecast (units/day)
     * @param leadTimeDays Supplier lead time in days
     * @param safetyStock Safety buffer from forecast model
     * @return Suggested reorder quantity (minimum 0)
     */
    private int recalculateSuggestedReorderQty(
            int currentStock,
            int targetStockLevel,
            BigDecimal muHat,
            int leadTimeDays,
            Integer safetyStock) {
        int baseQty = targetStockLevel - currentStock;

        // Add lead time demand (units sold while waiting for delivery)
        int leadTimeDemand = 0;
        if (muHat != null && muHat.compareTo(BigDecimal.ZERO) > 0) {
            leadTimeDemand = (int) Math.ceil(muHat.doubleValue() * leadTimeDays);
        }

        // Add safety buffer from forecast (or default to 0)
        int buffer = safetyStock != null ? safetyStock : 0;

        return Math.max(0, baseQty + leadTimeDemand + buffer);
    }

    /**
     * Check if an item is overdue: original suggested order date has passed AND stock is below reorder point.
     */
    private boolean isOverdue(LocalDate originalDate, int currentStock, int reorderPoint, LocalDate today) {
        if (originalDate == null) {
            return false;
        }
        return !originalDate.isAfter(today) && currentStock < reorderPoint;
    }

    @Transactional(readOnly = true)
    public List<CategoryInventoryDTO> getInventoryByCategory() {
        List<Product> products = productRepository.findAllWithCategories();
        Map<UUID, Integer> stockTotals = inventoryTotalsRepository.findAllStockTotalsMap();

        Map<Category, List<Product>> productsByCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        List<CategoryInventoryDTO> result = new ArrayList<>();

        for (Map.Entry<Category, List<Product>> entry : productsByCategory.entrySet()) {
            String categoryName = entry.getKey().getName();
            List<Product> categoryProducts = entry.getValue();

            int totalStock = categoryProducts.stream()
                    .mapToInt(p -> stockTotals.getOrDefault(p.getId(), 0))
                    .sum();

            result.add(new CategoryInventoryDTO(categoryName, (long) categoryProducts.size(), totalStock));
        }

        return result;
    }

    @Cacheable(CacheConfig.PERFORMANCE_METRICS_CACHE)
    @Transactional(readOnly = true)
    public PerformanceMetricsDTO getPerformanceMetrics() {
        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();

        // Calculate average forecast accuracy from MAPE
        BigDecimal avgAccuracy = BigDecimal.ZERO;
        int accuracyCount = 0;
        for (ForecastPrediction fp : latestPredictions) {
            ForecastFeatures features = extractFeatures(fp);
            BigDecimal accuracy = calculateForecastAccuracy(features.mape());
            if (accuracy != null) {
                avgAccuracy = avgAccuracy.add(accuracy);
                accuracyCount++;
            }
        }
        if (accuracyCount > 0) {
            avgAccuracy = avgAccuracy.divide(BigDecimal.valueOf(accuracyCount), 1, RoundingMode.HALF_UP);
        }

        List<Product> allProducts = productRepository.findAll();
        Map<UUID, Integer> stockByProduct = inventoryTotalsRepository.findAllStockTotalsMap();

        long outOfStockCount = allProducts.stream()
                .filter(p -> stockByProduct.getOrDefault(p.getId(), 0) == 0)
                .count();

        BigDecimal stockoutRate = BigDecimal.ZERO;
        if (!allProducts.isEmpty()) {
            double rate = (double) outOfStockCount / allProducts.size();
            stockoutRate = BigDecimal.valueOf(rate * 100).setScale(1, RoundingMode.HALF_UP);
        }

        BigDecimal fillRate = BigDecimal.valueOf(100).subtract(stockoutRate);

        // Turnover rate based on demand velocity instead of COGS
        BigDecimal turnoverRate = computeDemandBasedTurnover(latestPredictions, stockByProduct);

        return new PerformanceMetricsDTO(turnoverRate, avgAccuracy, stockoutRate, fillRate);
    }

    private BigDecimal computeDemandBasedTurnover(List<ForecastPrediction> predictions, Map<UUID, Integer> stockByProduct) {
        BigDecimal totalDemandVelocity = BigDecimal.ZERO;
        int totalStock = 0;

        for (ForecastPrediction fp : predictions) {
            ForecastFeatures features = extractFeatures(fp);
            if (features.muHat() != null) {
                totalDemandVelocity = totalDemandVelocity.add(features.muHat());
            }
            totalStock += stockByProduct.getOrDefault(fp.getItemId(), 0);
        }

        if (totalStock == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }

        // Stock velocity = total demand velocity / total stock * 365 (annualized)
        return totalDemandVelocity
            .multiply(BigDecimal.valueOf(365))
            .divide(BigDecimal.valueOf(totalStock), 1, RoundingMode.HALF_UP);
    }

    @Cacheable(CacheConfig.SALES_SUMMARY_CACHE)
    @Transactional(readOnly = true)
    public SalesSummaryDTO getSalesSummary() {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusMonths(12);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Use pre-aggregated rollup table instead of loading raw stock movements
        List<Object[]> monthlyRows = dailySalesRollupRepository.aggregateByMonth(periodStart);
        List<DailySalesRollup> dailyRollups = dailySalesRollupRepository
            .findByRollupDateBetweenOrderByRollupDateAsc(periodStart, today);
        List<Object[]> totalsList = dailySalesRollupRepository.getTotalsForPeriod(periodStart, today);
        Object[] totals = totalsList.isEmpty() ? new Object[]{0, BigDecimal.ZERO} : totalsList.get(0);

        List<MonthlySalesDTO> monthlySales = monthlyRows.stream()
            .map(row -> {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                String monthKey = String.format("%d-%02d", year, month);
                int units = ((Number) row[2]).intValue();
                BigDecimal revenue = row[3] instanceof BigDecimal bd
                    ? bd.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(((Number) row[3]).doubleValue()).setScale(2, RoundingMode.HALF_UP);
                return new MonthlySalesDTO(monthKey, revenue, units);
            })
            .toList();

        // Aggregate daily rollups by date (multiple items per day -> one row per day)
        Map<String, Integer> dailyUnits = new TreeMap<>();
        Map<String, BigDecimal> dailyRevenue = new TreeMap<>();
        for (DailySalesRollup rollup : dailyRollups) {
            String dateKey = rollup.getRollupDate().format(dateFormatter);
            dailyUnits.merge(dateKey, rollup.getUnitsSold(), Integer::sum);
            dailyRevenue.merge(dateKey, rollup.getRevenue(), BigDecimal::add);
        }

        List<DailySalesDTO> dailySales = dailyUnits.entrySet().stream()
            .map(entry -> new DailySalesDTO(
                entry.getKey(),
                entry.getValue(),
                dailyRevenue.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)
            ))
            .toList();

        int totalUnits = totals[0] != null ? ((Number) totals[0]).intValue() : 0;
        BigDecimal totalRevenue = totals[1] != null
            ? (totals[1] instanceof BigDecimal bd
                ? bd.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(((Number) totals[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP))
            : BigDecimal.ZERO;

        return new SalesSummaryDTO(
            monthlySales,
            dailySales,
            totalRevenue,
            totalUnits,
            periodStart.format(dateFormatter),
            today.format(dateFormatter)
        );
    }

    /**
     * Get action center data with demand-based metrics.
     * Returns items needing reorder decisions with demand velocity, volatility, and forecast accuracy.
     */
    @Cacheable(CacheConfig.PREDICTIONS_CACHE)
    @Transactional(readOnly = true)
    public ActionCenterDTO getActionCenter() {
        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
        List<Product> allProducts = productRepository.findAllWithCategories();
        Map<UUID, Product> productMap = allProducts.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ActionItem> actionItems = new ArrayList<>();
        int critical = 0, urgent = 0, attention = 0, healthy = 0;
        BigDecimal totalDemandVelocity = BigDecimal.ZERO;
        BigDecimal totalAccuracy = BigDecimal.ZERO;
        int accuracyCount = 0;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (ForecastPrediction prediction : latestPredictions) {
            Product product = productMap.get(prediction.getItemId());
            if (product == null || !product.getIsActive()) {
                continue;
            }

            int currentStock = stockMap.getOrDefault(prediction.getItemId(), 0);
            int leadTimeDays = product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 14;
            int rp = product.getReorderPoint() != null ? product.getReorderPoint() : 10;
            int targetStock = product.getTargetStockLevel() != null ? product.getTargetStockLevel() : 50;

            ForecastFeatures features = extractFeatures(prediction);
            BigDecimal demandVelocity = features.muHat();
            BigDecimal demandVolatility = calculateDemandVolatility(features.muHat(), features.sigmaDHat());
            BigDecimal forecastAccuracy = calculateForecastAccuracy(features.mape());

            // Recalculate dynamic fields from live stock + stored mu_hat
            BigDecimal daysToStockout = recalculateDaysToStockout(currentStock, features.muHat());
            LocalDate suggestedOrderDate = recalculateSuggestedOrderDate(daysToStockout, leadTimeDays, today);
            int suggestedReorderQty = recalculateSuggestedReorderQty(
                currentStock, targetStock, features.muHat(), leadTimeDays, features.safetyStock());
            // Use recalculated date so overdue badge is consistent with displayed order date
            boolean overdue = isOverdue(suggestedOrderDate, currentStock, rp, today);

            String computedAt = prediction.getComputedAt() != null
                ? prediction.getComputedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : null;

            if (demandVelocity != null) {
                totalDemandVelocity = totalDemandVelocity.add(demandVelocity);
            }
            if (forecastAccuracy != null) {
                totalAccuracy = totalAccuracy.add(forecastAccuracy);
                accuracyCount++;
            }

            ActionUrgency urgency = calculateUrgency(
                daysToStockout,
                leadTimeDays,
                currentStock,
                rp
            );

            switch (urgency) {
                case CRITICAL -> critical++;
                case URGENT -> urgent++;
                case ATTENTION -> attention++;
                case HEALTHY -> healthy++;
            }

            actionItems.add(new ActionItem(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getImageUrl(),
                product.getCategory() != null ? product.getCategory().getName() : "Uncategorized",
                currentStock,
                rp,
                targetStock,
                daysToStockout,
                prediction.getAvgDailyDelta(),
                suggestedReorderQty,
                suggestedOrderDate,
                leadTimeDays,
                demandVelocity,
                demandVolatility,
                forecastAccuracy,
                prediction.getConfidence(),
                urgency,
                overdue,
                computedAt
            ));
        }

        actionItems.sort(Comparator
            .comparing(ActionItem::urgency)
            .thenComparing(item -> item.daysToStockout() != null ? item.daysToStockout() : BigDecimal.valueOf(999)));

        BigDecimal avgForecastAccuracy = accuracyCount > 0
            ? totalAccuracy.divide(BigDecimal.valueOf(accuracyCount), 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new ActionCenterDTO(
            actionItems,
            actionItems.size(),
            avgForecastAccuracy,
            totalDemandVelocity.setScale(2, RoundingMode.HALF_UP),
            new RiskSummary(critical, urgent, attention, healthy)
        );
    }

    private ActionUrgency calculateUrgency(BigDecimal daysToStockout, int leadTimeDays, int currentStock, Integer reorderPoint) {
        if (daysToStockout == null) {
            return ActionUrgency.HEALTHY;
        }

        double days = daysToStockout.doubleValue();
        int rp = reorderPoint != null ? reorderPoint : 10;

        if (days < leadTimeDays) {
            return ActionUrgency.CRITICAL;
        }
        if (days < leadTimeDays * 2) {
            return ActionUrgency.URGENT;
        }
        if (days < leadTimeDays * 3 || currentStock < rp) {
            return ActionUrgency.ATTENTION;
        }

        return ActionUrgency.HEALTHY;
    }

    /**
     * Get insights with demand-based metrics.
     * Top movers use ACV-weighted velocity (demandVelocity × displayConfidence).
     */
    @Cacheable(CacheConfig.INSIGHTS_CACHE)
    @Transactional(readOnly = true)
    public InsightsDTO getInsights() {
        LocalDate today = LocalDate.now();
        LocalDate currentPeriodStart = today.minusDays(30);
        LocalDate previousPeriodStart = today.minusDays(60);
        LocalDate dowAnalysisStart = today.minusDays(90);

        List<DailySalesRollup> rollups = dailySalesRollupRepository
            .findByRollupDateBetweenOrderByRollupDateAsc(dowAnalysisStart, today);

        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();
        Map<UUID, ForecastFeatures> featuresMap = new HashMap<>();
        for (ForecastPrediction fp : latestPredictions) {
            featuresMap.put(fp.getItemId(), extractFeatures(fp));
        }

        // Load products once and share across movers computation
        List<Product> allProducts = productRepository.findAllWithCategories();
        Map<UUID, Product> productMap = allProducts.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        // Fetch display days for current period (for ACV-weighted velocity)
        OffsetDateTime currentStartOdt = currentPeriodStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime todayOdt = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        Map<UUID, Double> currentDisplayDays = machineDisplayRepository
            .calculateDisplayDaysByProduct(currentStartOdt, todayOdt)
            .stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> ((Number) row[1]).doubleValue()
            ));

        List<DayOfWeekPattern> dowPatterns = computeDowPatternsWithDemand(rollups, featuresMap);
        List<Mover> topMovers = computeMoversFromRollups(rollups, currentPeriodStart, previousPeriodStart, today, true, productMap, featuresMap, currentDisplayDays);
        List<Mover> bottomMovers = computeMoversFromRollups(rollups, currentPeriodStart, previousPeriodStart, today, false, productMap, featuresMap, currentDisplayDays);
        PeriodSummary currentPeriod = computePeriodSummaryWithDemand(rollups, currentPeriodStart, today, "Last 30 Days", featuresMap);
        PeriodSummary previousPeriod = computePeriodSummaryWithDemand(rollups, previousPeriodStart, currentPeriodStart.minusDays(1), "Previous 30 Days", featuresMap);

        return new InsightsDTO(
            dowPatterns,
            topMovers,
            bottomMovers,
            currentPeriod,
            previousPeriod
        );
    }

    private List<DayOfWeekPattern> computeDowPatternsWithDemand(
            List<DailySalesRollup> rollups, Map<UUID, ForecastFeatures> featuresMap) {

        Map<Integer, Integer> unitsByDow = new HashMap<>();
        Map<Integer, BigDecimal> multiplierSumByDow = new HashMap<>();
        Map<Integer, Integer> multiplierCountByDow = new HashMap<>();

        for (DailySalesRollup rollup : rollups) {
            int dow = rollup.getRollupDate().getDayOfWeek().getValue() % 7;
            unitsByDow.merge(dow, rollup.getUnitsSold(), Integer::sum);

            ForecastFeatures features = featuresMap.get(rollup.getItemId());
            if (features != null && features.dowMultipliers() != null && features.dowMultipliers().size() > dow) {
                BigDecimal multiplier = BigDecimal.valueOf(features.dowMultipliers().get(dow));
                multiplierSumByDow.merge(dow, multiplier, BigDecimal::add);
                multiplierCountByDow.merge(dow, 1, Integer::sum);
            }
        }

        int totalUnits = unitsByDow.values().stream().mapToInt(Integer::intValue).sum();

        List<DayOfWeekPattern> patterns = new ArrayList<>();
        for (int dow = 0; dow < 7; dow++) {
            int units = unitsByDow.getOrDefault(dow, 0);
            BigDecimal percent = totalUnits > 0
                ? BigDecimal.valueOf(units * 100.0 / totalUnits).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            int count = multiplierCountByDow.getOrDefault(dow, 0);
            BigDecimal avgMultiplier = count > 0
                ? multiplierSumByDow.getOrDefault(dow, BigDecimal.ONE)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

            patterns.add(new DayOfWeekPattern(dow, DOW_NAMES[dow], units, avgMultiplier, percent));
        }

        return patterns;
    }

    /**
     * Compute movers using ACV-weighted velocity ranking.
     * Score = demandVelocity × displayConfidence × (1 + growthBonus)
     */
    private List<Mover> computeMoversFromRollups(
            List<DailySalesRollup> rollups,
            LocalDate currentStart, LocalDate previousStart, LocalDate endDate,
            boolean topMovers, Map<UUID, Product> productMap,
            Map<UUID, ForecastFeatures> featuresMap,
            Map<UUID, Double> currentDisplayDays) {

        Map<UUID, Integer> currentPeriodUnits = new HashMap<>();
        Map<UUID, Integer> previousPeriodUnits = new HashMap<>();

        for (DailySalesRollup rollup : rollups) {
            LocalDate date = rollup.getRollupDate();
            if (!date.isBefore(currentStart) && !date.isAfter(endDate)) {
                currentPeriodUnits.merge(rollup.getItemId(), rollup.getUnitsSold(), Integer::sum);
            } else if (!date.isBefore(previousStart) && date.isBefore(currentStart)) {
                previousPeriodUnits.merge(rollup.getItemId(), rollup.getUnitsSold(), Integer::sum);
            }
        }

        List<Mover> movers = new ArrayList<>();
        for (UUID itemId : currentPeriodUnits.keySet()) {
            int current = currentPeriodUnits.get(itemId);
            int previous = previousPeriodUnits.getOrDefault(itemId, 0);
            if (previous == 0 && current == 0) continue;

            // Skip products with insufficient display history
            Double displayDays = currentDisplayDays.getOrDefault(itemId, 0.0);
            if (displayDays < MIN_DISPLAY_DAYS) continue;

            BigDecimal displayConfidence = BigDecimal.valueOf(Math.min(displayDays / PERIOD_DAYS, 1.0))
                .setScale(2, RoundingMode.HALF_UP);

            // Get demand velocity from forecast, fallback to estimated from sales
            ForecastFeatures features = featuresMap.get(itemId);
            BigDecimal demandVelocity = features != null ? features.muHat() : null;
            if (demandVelocity == null && current > 0 && displayDays > 0) {
                demandVelocity = BigDecimal.valueOf(current / displayDays)
                    .setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal percentChange;
            MoverDirection direction;
            if (previous == 0) {
                percentChange = BigDecimal.valueOf(100);
                direction = MoverDirection.UP;
            } else {
                double change = ((double) (current - previous) / previous) * 100;
                percentChange = BigDecimal.valueOf(change).setScale(1, RoundingMode.HALF_UP);
                direction = change > 5 ? MoverDirection.UP : (change < -5 ? MoverDirection.DOWN : MoverDirection.STABLE);
            }

            Product product = productMap.get(itemId);
            if (product == null) continue;

            movers.add(new Mover(
                0, // rank assigned after sorting
                itemId,
                product.getName(),
                product.getImageUrl(),
                product.getCategory() != null ? product.getCategory().getName() : "Uncategorized",
                current,
                previous,
                percentChange,
                direction,
                demandVelocity,
                displayConfidence
            ));
        }

        // ACV-weighted velocity comparator with growth bonus
        Comparator<Mover> comparator = topMovers
            ? Comparator.comparing((Mover m) -> {
                BigDecimal velocity = m.demandVelocity() != null ? m.demandVelocity() : BigDecimal.ZERO;
                BigDecimal confidence = m.displayConfidence() != null ? m.displayConfidence() : BigDecimal.ZERO;
                BigDecimal baseScore = velocity.multiply(confidence);
                double growthBonus = m.percentChange().doubleValue() > 0
                    ? Math.min(m.percentChange().doubleValue() / 100.0, 0.5)
                    : 0.0;
                return baseScore.multiply(BigDecimal.valueOf(1.0 + growthBonus));
            }).reversed()
            : Comparator.comparing(Mover::percentChange);

        List<Mover> sortedMovers = movers.stream()
            .filter(m -> topMovers ? m.direction() == MoverDirection.UP : m.direction() == MoverDirection.DOWN)
            .sorted(comparator)
            .limit(MOVERS_LIMIT)
            .toList();

        return assignRanks(sortedMovers, (rank, m) -> new Mover(
            rank, m.itemId(), m.name(), m.imageUrl(), m.categoryName(),
            m.currentPeriodUnits(), m.previousPeriodUnits(), m.percentChange(), m.direction(),
            m.demandVelocity(), m.displayConfidence()
        ));
    }

    private PeriodSummary computePeriodSummaryWithDemand(
            List<DailySalesRollup> rollups, LocalDate startDate, LocalDate endDate, String label,
            Map<UUID, ForecastFeatures> featuresMap) {

        int totalUnits = 0;
        int totalMovements = 0;
        Set<UUID> uniqueItems = new HashSet<>();

        for (DailySalesRollup rollup : rollups) {
            LocalDate date = rollup.getRollupDate();
            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                totalUnits += rollup.getUnitsSold();
                totalMovements += rollup.getMovementCount();
                if (rollup.getUnitsSold() > 0) {
                    uniqueItems.add(rollup.getItemId());
                }
            }
        }

        BigDecimal totalDemandVelocity = BigDecimal.ZERO;
        BigDecimal totalAccuracy = BigDecimal.ZERO;
        int accuracyCount = 0;

        for (UUID itemId : uniqueItems) {
            ForecastFeatures features = featuresMap.get(itemId);
            if (features != null) {
                if (features.muHat() != null) {
                    totalDemandVelocity = totalDemandVelocity.add(features.muHat());
                }
                BigDecimal accuracy = calculateForecastAccuracy(features.mape());
                if (accuracy != null) {
                    totalAccuracy = totalAccuracy.add(accuracy);
                    accuracyCount++;
                }
            }
        }

        BigDecimal avgDemandVelocity = !uniqueItems.isEmpty()
            ? totalDemandVelocity.divide(BigDecimal.valueOf(uniqueItems.size()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal avgAccuracy = accuracyCount > 0
            ? totalAccuracy.divide(BigDecimal.valueOf(accuracyCount), 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new PeriodSummary(label, totalUnits, avgDemandVelocity, avgAccuracy, uniqueItems.size(), totalMovements);
    }

    /**
     * Get demand leaders - rankings by demand velocity and stock velocity.
     */
    @Cacheable(value = CacheConfig.DEMAND_LEADERS_CACHE, key = "#period")
    @Transactional(readOnly = true)
    public DemandLeadersDTO getDemandLeaders(String period) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate previousStart;
        String periodLabel;

        switch (period != null ? period.toLowerCase() : "30d") {
            case "7d" -> {
                startDate = today.minusDays(7);
                previousStart = today.minusDays(14);
                periodLabel = "Last 7 Days";
            }
            case "90d" -> {
                startDate = today.minusDays(90);
                previousStart = today.minusDays(180);
                periodLabel = "Last 90 Days";
            }
            case "ytd" -> {
                startDate = today.withDayOfYear(1);
                previousStart = startDate.minusYears(1);
                periodLabel = "Year to Date";
            }
            default -> {
                startDate = today.minusDays(30);
                previousStart = today.minusDays(60);
                periodLabel = "Last 30 Days";
            }
        }

        List<Product> products = productRepository.findAllWithCategories();
        Map<UUID, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Category> categories = categoryRepository.findAll();
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();

        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();
        Map<UUID, ForecastFeatures> featuresMap = new HashMap<>();
        for (ForecastPrediction fp : latestPredictions) {
            featuresMap.put(fp.getItemId(), extractFeatures(fp));
        }

        // Fetch rollups for both current and previous periods in a single query
        List<DailySalesRollup> allRollups = dailySalesRollupRepository
            .findByRollupDateBetweenOrderByRollupDateAsc(previousStart, today);

        // Partition into current and previous periods in-memory
        Map<UUID, Integer> unitsByItem = new HashMap<>();
        int previousTotalUnits = 0;
        for (DailySalesRollup rollup : allRollups) {
            LocalDate rollupDate = rollup.getRollupDate();
            if (!rollupDate.isBefore(startDate)) {
                unitsByItem.merge(rollup.getItemId(), rollup.getUnitsSold(), Integer::sum);
            } else {
                previousTotalUnits += rollup.getUnitsSold();
            }
        }

        // Calculate demand metrics for each item
        Map<UUID, BigDecimal> demandVelocityByItem = new HashMap<>();
        Map<UUID, BigDecimal> stockVelocityByItem = new HashMap<>();
        Map<UUID, BigDecimal> volatilityByItem = new HashMap<>();
        Map<UUID, BigDecimal> accuracyByItem = new HashMap<>();

        BigDecimal totalDemandVelocity = BigDecimal.ZERO;
        BigDecimal totalAccuracy = BigDecimal.ZERO;
        int accuracyCount = 0;

        for (Product product : products) {
            ForecastFeatures features = featuresMap.get(product.getId());
            if (features != null && features.muHat() != null) {
                BigDecimal muHat = features.muHat();
                demandVelocityByItem.put(product.getId(), muHat);
                totalDemandVelocity = totalDemandVelocity.add(muHat);

                int stock = stockMap.getOrDefault(product.getId(), 0);
                if (stock > 0) {
                    BigDecimal stockVelocity = muHat.divide(BigDecimal.valueOf(stock), 4, RoundingMode.HALF_UP);
                    stockVelocityByItem.put(product.getId(), stockVelocity);
                }

                BigDecimal volatility = calculateDemandVolatility(muHat, features.sigmaDHat());
                if (volatility != null) {
                    volatilityByItem.put(product.getId(), volatility);
                }

                BigDecimal accuracy = calculateForecastAccuracy(features.mape());
                if (accuracy != null) {
                    accuracyByItem.put(product.getId(), accuracy);
                    totalAccuracy = totalAccuracy.add(accuracy);
                    accuracyCount++;
                }
            }
        }

        // Leaders by demand velocity
        final BigDecimal finalTotalDemandVelocity = totalDemandVelocity;
        List<DemandLeader> byDemandVelocity = demandVelocityByItem.entrySet().stream()
            .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
            .limit(MOVERS_LIMIT)
            .map(entry -> {
                UUID itemId = entry.getKey();
                Product product = productMap.get(itemId);
                if (product == null) return null;

                BigDecimal demandVelocity = entry.getValue();
                BigDecimal percentOfTotal = finalTotalDemandVelocity.compareTo(BigDecimal.ZERO) > 0
                    ? demandVelocity.multiply(BigDecimal.valueOf(100))
                        .divide(finalTotalDemandVelocity, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return new DemandLeader(
                    0,
                    itemId,
                    product.getName(),
                    product.getSku(),
                    product.getImageUrl(),
                    product.getCategory() != null ? product.getCategory().getName() : "Uncategorized",
                    unitsByItem.getOrDefault(itemId, 0),
                    demandVelocity.setScale(2, RoundingMode.HALF_UP),
                    volatilityByItem.getOrDefault(itemId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                    accuracyByItem.getOrDefault(itemId, BigDecimal.ZERO),
                    stockVelocityByItem.getOrDefault(itemId, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP),
                    percentOfTotal
                );
            })
            .filter(Objects::nonNull)
            .toList();

        List<DemandLeader> rankedByDemandVelocity = assignRanks(byDemandVelocity, (rank, dl) -> new DemandLeader(
            rank, dl.itemId(), dl.name(), dl.sku(), dl.imageUrl(), dl.categoryName(),
            dl.periodDemand(), dl.demandVelocity(), dl.demandVolatility(), dl.forecastAccuracy(),
            dl.stockVelocity(), dl.percentOfTotal()));

        // Leaders by stock velocity
        List<DemandLeader> byStockVelocity = stockVelocityByItem.entrySet().stream()
            .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
            .limit(MOVERS_LIMIT)
            .map(entry -> {
                UUID itemId = entry.getKey();
                Product product = productMap.get(itemId);
                if (product == null) return null;

                BigDecimal demandVelocity = demandVelocityByItem.getOrDefault(itemId, BigDecimal.ZERO);
                BigDecimal percentOfTotal = finalTotalDemandVelocity.compareTo(BigDecimal.ZERO) > 0
                    ? demandVelocity.multiply(BigDecimal.valueOf(100))
                        .divide(finalTotalDemandVelocity, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return new DemandLeader(
                    0,
                    itemId,
                    product.getName(),
                    product.getSku(),
                    product.getImageUrl(),
                    product.getCategory() != null ? product.getCategory().getName() : "Uncategorized",
                    unitsByItem.getOrDefault(itemId, 0),
                    demandVelocity.setScale(2, RoundingMode.HALF_UP),
                    volatilityByItem.getOrDefault(itemId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                    accuracyByItem.getOrDefault(itemId, BigDecimal.ZERO),
                    entry.getValue().setScale(4, RoundingMode.HALF_UP),
                    percentOfTotal
                );
            })
            .filter(Objects::nonNull)
            .toList();

        List<DemandLeader> rankedByStockVelocity = assignRanks(byStockVelocity, (rank, dl) -> new DemandLeader(
            rank, dl.itemId(), dl.name(), dl.sku(), dl.imageUrl(), dl.categoryName(),
            dl.periodDemand(), dl.demandVelocity(), dl.demandVolatility(), dl.forecastAccuracy(),
            dl.stockVelocity(), dl.percentOfTotal()));

        // Category rankings by demand velocity
        Map<UUID, BigDecimal> demandVelocityByCategory = new HashMap<>();
        Map<UUID, Integer> unitsByCategory = new HashMap<>();
        for (UUID itemId : demandVelocityByItem.keySet()) {
            Product product = productMap.get(itemId);
            if (product == null || product.getCategory() == null) continue;
            UUID categoryId = product.getCategory().getId();
            demandVelocityByCategory.merge(categoryId, demandVelocityByItem.get(itemId), BigDecimal::add);
            unitsByCategory.merge(categoryId, unitsByItem.getOrDefault(itemId, 0), Integer::sum);
        }

        final BigDecimal finalTotalDemandVelocity2 = totalDemandVelocity;
        List<CategoryRanking> categoryRankings = categories.stream()
            .filter(cat -> demandVelocityByCategory.containsKey(cat.getId()))
            .sorted(Comparator.comparing((Category cat) ->
                demandVelocityByCategory.getOrDefault(cat.getId(), BigDecimal.ZERO)).reversed())
            .limit(CATEGORY_RANKINGS_LIMIT)
            .map(cat -> {
                UUID catId = cat.getId();
                BigDecimal catDemandVelocity = demandVelocityByCategory.getOrDefault(catId, BigDecimal.ZERO);
                int catUnits = unitsByCategory.getOrDefault(catId, 0);
                int totalItems = (int) products.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(catId))
                    .count();
                BigDecimal percentOfTotal = finalTotalDemandVelocity2.compareTo(BigDecimal.ZERO) > 0
                    ? catDemandVelocity.multiply(BigDecimal.valueOf(100))
                        .divide(finalTotalDemandVelocity2, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return new CategoryRanking(0, catId, cat.getName(), totalItems, catUnits,
                    catDemandVelocity.setScale(2, RoundingMode.HALF_UP), percentOfTotal);
            })
            .toList();

        List<CategoryRanking> rankedCategories = assignRanks(categoryRankings, (rank, cr) ->
            new CategoryRanking(rank, cr.categoryId(), cr.categoryName(),
                cr.totalItems(), cr.periodDemand(), cr.totalDemandVelocity(), cr.percentOfTotal()));

        // Growth calculation using already-partitioned data
        int currentTotalUnits = unitsByItem.values().stream().mapToInt(Integer::intValue).sum();

        BigDecimal demandGrowth = BigDecimal.ZERO;
        if (previousTotalUnits > 0) {
            demandGrowth = BigDecimal.valueOf((currentTotalUnits - previousTotalUnits) * 100.0 / previousTotalUnits)
                .setScale(1, RoundingMode.HALF_UP);
        }

        BigDecimal systemAccuracy = accuracyCount > 0
            ? totalAccuracy.divide(BigDecimal.valueOf(accuracyCount), 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        DemandSummary summary = new DemandSummary(
            totalDemandVelocity.setScale(2, RoundingMode.HALF_UP),
            currentTotalUnits,
            demandVelocityByItem.size(),
            demandGrowth,
            systemAccuracy,
            periodLabel
        );

        return new DemandLeadersDTO(rankedByDemandVelocity, rankedByStockVelocity, rankedCategories, summary);
    }
}
