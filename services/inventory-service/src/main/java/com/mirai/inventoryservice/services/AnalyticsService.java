package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.CategoryInventoryDTO;
import com.mirai.inventoryservice.dtos.responses.DailySalesDTO;
import com.mirai.inventoryservice.dtos.responses.MonthlySalesDTO;
import com.mirai.inventoryservice.dtos.responses.PerformanceMetricsDTO;
import com.mirai.inventoryservice.dtos.responses.SalesSummaryDTO;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.InventoryTotalsRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ProductRepository productRepository;
    private final ForecastPredictionRepository forecastPredictionRepository;
    private final InventoryTotalsRepository inventoryTotalsRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public List<CategoryInventoryDTO> getInventoryByCategory() {
        List<Product> products = productRepository.findAllWithCategories();

        // Bulk fetch all stock totals in a single query
        Map<UUID, Integer> stockTotals = inventoryTotalsRepository.findAllStockTotalsMap();

        // Group products by category
        Map<Category, List<Product>> productsByCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        List<CategoryInventoryDTO> result = new ArrayList<>();

        for (Map.Entry<Category, List<Product>> entry : productsByCategory.entrySet()) {
            String categoryName = entry.getKey().getName();
            List<Product> categoryProducts = entry.getValue();

            // Calculate total stock for this category using pre-fetched map
            int totalStock = categoryProducts.stream()
                    .mapToInt(p -> stockTotals.getOrDefault(p.getId(), 0))
                    .sum();

            result.add(new CategoryInventoryDTO(categoryName, (long) categoryProducts.size(), totalStock));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public PerformanceMetricsDTO getPerformanceMetrics() {
        // 1. Forecast Accuracy: Average confidence of latest prediction per item only
        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAllLatest();

        BigDecimal avgConfidence = BigDecimal.ZERO;
        if (!latestPredictions.isEmpty()) {
            double average = latestPredictions.stream()
                    .map(ForecastPrediction::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0.0);
            avgConfidence = BigDecimal.valueOf(average * 100).setScale(1, RoundingMode.HALF_UP);
        }

        // 2. Stockout Rate: % of items with 0 stock (now includes all 9 inventory tables)
        List<Product> allProducts = productRepository.findAll();

        // Bulk fetch all stock totals in a single query
        Map<UUID, Integer> stockByProduct = inventoryTotalsRepository.findAllStockTotalsMap();

        long outOfStockCount = allProducts.stream()
                .filter(p -> stockByProduct.getOrDefault(p.getId(), 0) == 0)
                .count();

        BigDecimal stockoutRate = BigDecimal.ZERO;
        if (!allProducts.isEmpty()) {
            double rate = (double) outOfStockCount / allProducts.size();
            stockoutRate = BigDecimal.valueOf(rate * 100).setScale(1, RoundingMode.HALF_UP);
        }

        // 3. Fill Rate: inverse of stockout rate
        BigDecimal fillRate = BigDecimal.valueOf(100).subtract(stockoutRate);

        // 4. Turnover Rate: COGS (last 12 months) / Average Inventory Value
        BigDecimal turnoverRate = computeTurnoverRate(allProducts, stockByProduct);

        return new PerformanceMetricsDTO(turnoverRate, avgConfidence, stockoutRate, fillRate);
    }

    private BigDecimal computeTurnoverRate(List<Product> allProducts, Map<UUID, Integer> stockByProduct) {
        LocalDate today = LocalDate.now();
        OffsetDateTime startDateTime = today.minusMonths(12).atStartOfDay().atOffset(ZoneOffset.UTC);

        // COGS: sum of abs(quantityChange) * unitCost for SALE movements in last 12 months
        // Use JOIN FETCH query to avoid N+1 on item
        List<StockMovement> salesMovements = stockMovementRepository.findByReasonAndAtAfterWithItem(
                StockMovementReason.SALE, startDateTime);

        BigDecimal cogs = BigDecimal.ZERO;
        for (StockMovement movement : salesMovements) {
            int units = Math.abs(movement.getQuantityChange());
            BigDecimal unitCost = movement.getItem().getUnitCost();
            if (unitCost != null) {
                cogs = cogs.add(unitCost.multiply(BigDecimal.valueOf(units)));
            }
        }

        // Average Inventory Value: current snapshot of stock * unitCost across all active products
        BigDecimal inventoryValue = BigDecimal.ZERO;
        for (Product product : allProducts) {
            int currentStock = stockByProduct.getOrDefault(product.getId(), 0);
            BigDecimal unitCost = product.getUnitCost();
            if (unitCost != null && currentStock > 0) {
                inventoryValue = inventoryValue.add(unitCost.multiply(BigDecimal.valueOf(currentStock)));
            }
        }

        if (inventoryValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }

        return cogs.divide(inventoryValue, 1, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public SalesSummaryDTO getSalesSummary() {
        LocalDate today = LocalDate.now();
        // Rolling 12 months for consistent chart display
        LocalDate periodStart = today.minusMonths(12);
        OffsetDateTime startDateTime = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Use JOIN FETCH query to avoid N+1 on item
        List<StockMovement> salesMovements = stockMovementRepository.findByReasonAndAtAfterWithItem(
                StockMovementReason.SALE, startDateTime);

        Map<String, BigDecimal> monthlyRevenue = new TreeMap<>();
        Map<String, Integer> monthlyUnits = new TreeMap<>();
        Map<String, Integer> dailyUnits = new TreeMap<>();
        Map<String, BigDecimal> dailyRevenue = new TreeMap<>();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        int totalUnits = 0;

        for (StockMovement movement : salesMovements) {
            int units = Math.abs(movement.getQuantityChange());
            BigDecimal unitCost = movement.getItem().getUnitCost();
            BigDecimal revenue = unitCost != null
                ? unitCost.multiply(BigDecimal.valueOf(units))
                : BigDecimal.ZERO;

            String monthKey = movement.getAt().format(monthFormatter);
            String dateKey = movement.getAt().format(dateFormatter);

            monthlyRevenue.merge(monthKey, revenue, BigDecimal::add);
            monthlyUnits.merge(monthKey, units, Integer::sum);
            dailyUnits.merge(dateKey, units, Integer::sum);
            dailyRevenue.merge(dateKey, revenue, BigDecimal::add);

            totalRevenue = totalRevenue.add(revenue);
            totalUnits += units;
        }

        List<MonthlySalesDTO> monthlySales = monthlyRevenue.entrySet().stream()
            .map(entry -> new MonthlySalesDTO(
                entry.getKey(),
                entry.getValue().setScale(2, RoundingMode.HALF_UP),
                monthlyUnits.getOrDefault(entry.getKey(), 0)
            ))
            .toList();

        List<DailySalesDTO> dailySales = dailyUnits.entrySet().stream()
            .map(entry -> new DailySalesDTO(
                entry.getKey(),
                entry.getValue(),
                dailyRevenue.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)
            ))
            .toList();

        return new SalesSummaryDTO(
            monthlySales,
            dailySales,
            totalRevenue.setScale(2, RoundingMode.HALF_UP),
            totalUnits,
            periodStart.format(dateFormatter),
            today.format(dateFormatter)
        );
    }
}
