package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.config.CacheConfig;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.analytics.DailySalesRollup;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.DailySalesRollupRepository;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.InventoryTotalsRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying that Spring caching works correctly on AnalyticsService.
 * Uses @SpyBean on repositories to count actual invocations through the cache layer.
 *
 * NOTE: Because AnalyticsService methods use @Cacheable, Spring proxies must be used.
 * These tests inject the real service (not a mock) so the cache interceptor is active.
 * The @Transactional on BaseIntegrationTest ensures test data is rolled back.
 */
@Transactional
class AnalyticsCacheIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private ForecastPredictionRepository forecastPredictionRepository;

    @SpyBean
    private ProductRepository productRepository;

    @SpyBean
    private InventoryTotalsRepository inventoryTotalsRepository;

    @SpyBean
    private DailySalesRollupRepository dailySalesRollupRepository;

    @SpyBean
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository realProductRepository;

    @Autowired
    private CategoryRepository realCategoryRepository;

    @Autowired
    private ForecastPredictionRepository realForecastPredictionRepository;

    @Autowired
    private DailySalesRollupRepository realDailySalesRollupRepository;

    private UUID testProductId;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @BeforeEach
    void seedTestData() {
        Category category = Category.builder()
                .name("Test Category")
                .slug("test-category")
                .isActive(true)
                .displayOrder(1)
                .build();
        category = realCategoryRepository.save(category);

        Product product = Product.builder()
                .name("Test Product")
                .sku("TST-001")
                .category(category)
                .isActive(true)
                .reorderPoint(10)
                .targetStockLevel(50)
                .leadTimeDays(14)
                .quantity(30)
                .build();
        product = realProductRepository.save(product);
        testProductId = product.getId();

        Map<String, Object> features = new HashMap<>();
        features.put("mu_hat", 2.5);
        features.put("sigma_d_hat", 0.8);
        features.put("mape", 0.15);

        ForecastPrediction prediction = ForecastPrediction.builder()
                .itemId(testProductId)
                .horizonDays(30)
                .avgDailyDelta(BigDecimal.valueOf(-2.5))
                .daysToStockout(BigDecimal.valueOf(12))
                .suggestedReorderQty(25)
                .suggestedOrderDate(LocalDate.now().plusDays(5))
                .confidence(BigDecimal.valueOf(0.85))
                .features(features)
                .computedAt(OffsetDateTime.now())
                .build();
        realForecastPredictionRepository.save(prediction);

        DailySalesRollup rollup = DailySalesRollup.builder()
                .itemId(testProductId)
                .rollupDate(LocalDate.now().minusDays(1))
                .unitsSold(10)
                .revenue(BigDecimal.valueOf(50.00))
                .restockUnits(0)
                .damageUnits(0)
                .movementCount(2)
                .build();
        realDailySalesRollupRepository.save(rollup);
    }

    @Test
    @DisplayName("getActionCenter cache hit: second call does not hit ForecastPredictionRepository")
    void actionCenterCacheHitSkipsRepository() {
        analyticsService.getActionCenter();
        reset(forecastPredictionRepository);

        analyticsService.getActionCenter();

        verify(forecastPredictionRepository, never()).findAllLatest();
        verifyNoInteractions(forecastPredictionRepository);
    }

    @Test
    @DisplayName("getDemandLeaders uses different cache keys per period parameter")
    void demandLeadersDifferentCacheKeysPerPeriod() {
        // First call with "30d" - hits repository
        analyticsService.getDemandLeaders("30d");
        verify(forecastPredictionRepository, times(1)).findAllLatest();

        reset(forecastPredictionRepository, dailySalesRollupRepository,
                productRepository, categoryRepository, inventoryTotalsRepository);

        // Call with "7d" - different cache key, should hit repository again
        analyticsService.getDemandLeaders("7d");
        verify(forecastPredictionRepository, times(1)).findAllLatest();

        reset(forecastPredictionRepository, dailySalesRollupRepository,
                productRepository, categoryRepository, inventoryTotalsRepository);

        // Second call with "30d" - should be cached, no repository call
        analyticsService.getDemandLeaders("30d");
        verify(forecastPredictionRepository, never()).findAllLatest();
        verifyNoInteractions(forecastPredictionRepository);
    }

    @Test
    @DisplayName("cache eviction causes repository to be called again")
    void cacheEvictionTriggersRepositoryCall() {
        // First call populates cache
        analyticsService.getActionCenter();
        verify(forecastPredictionRepository, times(1)).findAllLatest();

        reset(forecastPredictionRepository, productRepository, inventoryTotalsRepository);

        // Evict the predictions cache programmatically
        Cache predictionsCache = cacheManager.getCache(CacheConfig.PREDICTIONS_CACHE);
        assertNotNull(predictionsCache, "Predictions cache should exist");
        predictionsCache.clear();

        // Third call should hit repository again after eviction
        analyticsService.getActionCenter();
        verify(forecastPredictionRepository, times(1)).findAllLatest();
    }

    @Test
    @DisplayName("all expected caches are registered in CacheManager")
    void allExpectedCachesExist() {
        assertNotNull(cacheManager.getCache(CacheConfig.PREDICTIONS_CACHE));
        assertNotNull(cacheManager.getCache(CacheConfig.INSIGHTS_CACHE));
        assertNotNull(cacheManager.getCache(CacheConfig.DEMAND_LEADERS_CACHE));
        assertNotNull(cacheManager.getCache(CacheConfig.SALES_SUMMARY_CACHE));
    }
}
