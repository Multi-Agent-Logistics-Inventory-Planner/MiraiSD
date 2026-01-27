package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.CategoryInventoryDTO;
import com.mirai.inventoryservice.dtos.responses.DailySalesDTO;
import com.mirai.inventoryservice.dtos.responses.MonthlySalesDTO;
import com.mirai.inventoryservice.dtos.responses.PerformanceMetricsDTO;
import com.mirai.inventoryservice.dtos.responses.SalesSummaryDTO;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.audit.StockMovement;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ProductRepository productRepository;
    private final ForecastPredictionRepository forecastPredictionRepository;
    private final ForecastService forecastService;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public List<CategoryInventoryDTO> getInventoryByCategory() {
        List<Product> products = productRepository.findAll();
        
        // Group products by category
        Map<ProductCategory, List<Product>> productsByCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory));
        
        List<CategoryInventoryDTO> result = new ArrayList<>();
        
        // Use hardcoded categories from enum to ensure all are represented or just present ones
        for (Map.Entry<ProductCategory, List<Product>> entry : productsByCategory.entrySet()) {
            String categoryName = entry.getKey().name();
            List<Product> categoryProducts = entry.getValue();
            
            // Calculate total stock for this category
            int totalStock = 0;
            for (Product p : categoryProducts) {
                // Reuse the logic from ForecastService to get stock from all inventory tables
                // We'll need to expose a public method in ForecastService or move that logic to a shared helper
                // For now, let's assume we can add a method to ForecastService
                totalStock += forecastService.getCurrentStockPublic(p.getId()); 
            }
            
            result.add(new CategoryInventoryDTO(categoryName, (long) categoryProducts.size(), totalStock));
        }
        
        return result;
    }

    @Transactional(readOnly = true)
    public PerformanceMetricsDTO getPerformanceMetrics() {
        // 1. Forecast Accuracy: Average confidence of latest predictions
        List<ForecastPrediction> latestPredictions = forecastPredictionRepository.findAll(); // simplified, ideally distinct by item
        // In reality we should query: select distinct on (item_id) * from forecast_predictions order by item_id, computed_at desc
        // But for now let's just take average of all recent predictions (or we can use a custom query)
        
        BigDecimal avgConfidence = BigDecimal.ZERO;
        if (!latestPredictions.isEmpty()) {
            double average = latestPredictions.stream()
                    .map(ForecastPrediction::getConfidence)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0.0);
            avgConfidence = BigDecimal.valueOf(average * 100).setScale(1, RoundingMode.HALF_UP);
        }

        // 2. Stockout Rate: % of items with 0 stock
        List<Product> allProducts = productRepository.findAll();
        long outOfStockCount = 0;
        for (Product p : allProducts) {
            if (forecastService.getCurrentStockPublic(p.getId()) == 0) {
                outOfStockCount++;
            }
        }
        
        BigDecimal stockoutRate = BigDecimal.ZERO;
        if (!allProducts.isEmpty()) {
            double rate = (double) outOfStockCount / allProducts.size();
            stockoutRate = BigDecimal.valueOf(rate * 100).setScale(1, RoundingMode.HALF_UP);
        }

        // 3. Fill Rate: (Total Items - Out of Stock) / Total Items (Inverse of stockout for now)
        BigDecimal fillRate = BigDecimal.valueOf(100).subtract(stockoutRate);

        // 4. Turnover Rate: Placeholder (requires sales history which we don't have yet)
        BigDecimal turnoverRate = new BigDecimal("4.2");

        return new PerformanceMetricsDTO(turnoverRate, avgConfidence, stockoutRate, fillRate);
    }

    @Transactional(readOnly = true)
    public SalesSummaryDTO getSalesSummary() {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusMonths(12);
        OffsetDateTime startDateTime = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Specification<StockMovement> salesSpec = (root, query, cb) ->
            cb.and(
                cb.equal(root.get("reason"), StockMovementReason.SALE),
                cb.greaterThanOrEqualTo(root.get("at"), startDateTime)
            );

        List<StockMovement> salesMovements = stockMovementRepository.findAll(salesSpec);

        Map<String, BigDecimal> monthlyRevenue = new TreeMap<>();
        Map<String, Integer> monthlyUnits = new TreeMap<>();
        Map<String, Integer> dailyUnits = new TreeMap<>();

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
            .map(entry -> new DailySalesDTO(entry.getKey(), entry.getValue()))
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
