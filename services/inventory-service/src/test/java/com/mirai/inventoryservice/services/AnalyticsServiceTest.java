package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsService.
 * Verifies that the refactored service makes the expected number of repository
 * calls (single-query patterns instead of redundant N+1 queries).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ForecastPredictionRepository forecastPredictionRepository;

    @Mock
    private InventoryTotalsRepository inventoryTotalsRepository;

    @Mock
    private DailySalesRollupRepository dailySalesRollupRepository;

    @Mock
    private MachineDisplayRepository machineDisplayRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Product buildProduct(UUID id, String name, String sku, Category category) {
        return Product.builder()
                .id(id)
                .name(name)
                .sku(sku)
                .category(category)
                .isActive(true)
                .reorderPoint(10)
                .targetStockLevel(50)
                .leadTimeDays(14)
                .build();
    }

    private Category buildCategory(UUID id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .isActive(true)
                .build();
    }

    private ForecastPrediction buildPrediction(UUID itemId) {
        Map<String, Object> features = new HashMap<>();
        features.put("mu_hat", 2.5);
        features.put("sigma_d_hat", 0.8);
        features.put("mape", 0.15);

        return ForecastPrediction.builder()
                .id(UUID.randomUUID())
                .itemId(itemId)
                .horizonDays(30)
                .avgDailyDelta(BigDecimal.valueOf(-2.5))
                .daysToStockout(BigDecimal.valueOf(20))
                .suggestedReorderQty(25)
                .suggestedOrderDate(LocalDate.now().plusDays(6))
                .confidence(BigDecimal.valueOf(0.85))
                .features(features)
                .build();
    }

    @Nested
    @DisplayName("getInsights")
    class GetInsights {

        @Test
        @DisplayName("calls productRepository.findAllWithCategories exactly once")
        void callsFindAllWithCategoriesOnce() {
            UUID productId = UUID.randomUUID();
            Category category = buildCategory(UUID.randomUUID(), "Figures");
            Product product = buildProduct(productId, "Test Figure", "FIG-001", category);

            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(forecastPredictionRepository.findAllLatest())
                    .thenReturn(Collections.emptyList());
            when(productRepository.findAllWithCategories())
                    .thenReturn(List.of(product));
            when(machineDisplayRepository.calculateDisplayDaysByProduct(any(), any()))
                    .thenReturn(Collections.emptyList());

            analyticsService.getInsights();

            verify(productRepository, times(1)).findAllWithCategories();
            verifyNoMoreInteractions(productRepository);
        }

        @Test
        @DisplayName("returns valid DTO with empty data")
        void returnsValidDtoWithEmptyData() {
            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(forecastPredictionRepository.findAllLatest())
                    .thenReturn(Collections.emptyList());
            when(productRepository.findAllWithCategories())
                    .thenReturn(Collections.emptyList());
            when(machineDisplayRepository.calculateDisplayDaysByProduct(any(), any()))
                    .thenReturn(Collections.emptyList());

            InsightsDTO result = analyticsService.getInsights();

            assertNotNull(result);
            assertNotNull(result.dayOfWeekPatterns());
            assertEquals(7, result.dayOfWeekPatterns().size());
            assertNotNull(result.topMovers());
            assertNotNull(result.bottomMovers());
        }
    }

    @Nested
    @DisplayName("getDemandLeaders")
    class GetDemandLeaders {

        @Test
        @DisplayName("calls dailySalesRollupRepository.findByRollupDateBetween exactly once per invocation")
        void callsRollupRepositoryOnce() {
            UUID productId = UUID.randomUUID();
            Category category = buildCategory(UUID.randomUUID(), "Plush");
            Product product = buildProduct(productId, "Test Plush", "PLU-001", category);

            when(productRepository.findAllWithCategories())
                    .thenReturn(List.of(product));
            when(categoryRepository.findAll())
                    .thenReturn(List.of(category));
            when(inventoryTotalsRepository.findAllStockTotalsMap())
                    .thenReturn(Map.of(productId, 30));
            when(forecastPredictionRepository.findAllLatest())
                    .thenReturn(List.of(buildPrediction(productId)));
            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(Collections.emptyList());

            analyticsService.getDemandLeaders("30d");

            verify(dailySalesRollupRepository, times(1))
                    .findByRollupDateBetweenOrderByRollupDateAsc(any(), any());
        }

        @Test
        @DisplayName("uses different date ranges for different periods")
        void usesDifferentDateRangesForDifferentPeriods() {
            when(productRepository.findAllWithCategories()).thenReturn(Collections.emptyList());
            when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
            when(inventoryTotalsRepository.findAllStockTotalsMap()).thenReturn(Collections.emptyMap());
            when(forecastPredictionRepository.findAllLatest()).thenReturn(Collections.emptyList());
            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(Collections.emptyList());

            DemandLeadersDTO result7d = analyticsService.getDemandLeaders("7d");
            DemandLeadersDTO result90d = analyticsService.getDemandLeaders("90d");

            assertNotNull(result7d);
            assertNotNull(result90d);
            assertEquals("Last 7 Days", result7d.summary().periodLabel());
            assertEquals("Last 90 Days", result90d.summary().periodLabel());
        }

        @Test
        @DisplayName("returns ranked leaders when forecast data exists")
        void returnsRankedLeadersWithForecastData() {
            UUID productId = UUID.randomUUID();
            Category category = buildCategory(UUID.randomUUID(), "Figures");
            Product product = buildProduct(productId, "Hot Figure", "FIG-002", category);

            when(productRepository.findAllWithCategories()).thenReturn(List.of(product));
            when(categoryRepository.findAll()).thenReturn(List.of(category));
            when(inventoryTotalsRepository.findAllStockTotalsMap()).thenReturn(Map.of(productId, 50));
            when(forecastPredictionRepository.findAllLatest())
                    .thenReturn(List.of(buildPrediction(productId)));
            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(Collections.emptyList());

            DemandLeadersDTO result = analyticsService.getDemandLeaders("30d");

            assertNotNull(result);
            assertFalse(result.byDemandVelocity().isEmpty());
            assertEquals(1, result.byDemandVelocity().get(0).rank());
        }
    }

    @Nested
    @DisplayName("getSalesSummary")
    class GetSalesSummary {

        @Test
        @DisplayName("does not call any StockMovementRepository and uses dailySalesRollupRepository.aggregateByMonth")
        void usesRollupAggregationInsteadOfStockMovements() {
            when(dailySalesRollupRepository.aggregateByMonth(any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(dailySalesRollupRepository.getTotalsForPeriod(any(), any()))
                    .thenReturn(Collections.singletonList(new Object[]{0L, BigDecimal.ZERO}));

            SalesSummaryDTO result = analyticsService.getSalesSummary();

            // Verify the rollup-based aggregation is called
            verify(dailySalesRollupRepository, times(1)).aggregateByMonth(any(LocalDate.class));
            verify(dailySalesRollupRepository, times(1)).getTotalsForPeriod(any(), any());
            verify(dailySalesRollupRepository, times(1))
                    .findByRollupDateBetweenOrderByRollupDateAsc(any(), any());

            // StockMovementRepository is not even a field on AnalyticsService,
            // so no interaction is possible. This test documents that architectural decision.
            assertNotNull(result);
            assertEquals(0, result.totalUnits());
        }

        @Test
        @DisplayName("returns monthly and daily sales from rollup data")
        void returnsMonthlySalesFromRollupData() {
            LocalDate today = LocalDate.now();
            Object[] monthlyRow = new Object[]{today.getYear(), today.getMonthValue(), 100, BigDecimal.valueOf(500.00)};
            List<Object[]> monthlyRows = new java.util.ArrayList<>();
            monthlyRows.add(monthlyRow);
            when(dailySalesRollupRepository.aggregateByMonth(any(LocalDate.class)))
                    .thenReturn(monthlyRows);

            DailySalesRollup rollup = DailySalesRollup.builder()
                    .id(UUID.randomUUID())
                    .itemId(UUID.randomUUID())
                    .rollupDate(today)
                    .unitsSold(25)
                    .revenue(BigDecimal.valueOf(125.00))
                    .movementCount(3)
                    .build();
            when(dailySalesRollupRepository.findByRollupDateBetweenOrderByRollupDateAsc(any(), any()))
                    .thenReturn(List.of(rollup));
            when(dailySalesRollupRepository.getTotalsForPeriod(any(), any()))
                    .thenReturn(Collections.singletonList(new Object[]{100L, BigDecimal.valueOf(500.00)}));

            SalesSummaryDTO result = analyticsService.getSalesSummary();

            assertNotNull(result);
            assertEquals(1, result.monthlySales().size());
            assertFalse(result.dailySales().isEmpty());
            assertEquals(100, result.totalUnits());
        }
    }
}
