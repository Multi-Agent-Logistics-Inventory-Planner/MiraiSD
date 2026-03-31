package com.mirai.inventoryservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PREDICTIONS_CACHE = "analytics-predictions";
    public static final String INSIGHTS_CACHE = "analytics-insights";
    public static final String DEMAND_LEADERS_CACHE = "analytics-demand-leaders";
    public static final String SALES_SUMMARY_CACHE = "analytics-sales-summary";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            buildCache(PREDICTIONS_CACHE, 2, TimeUnit.MINUTES, 10),
            buildCache(INSIGHTS_CACHE, 60, TimeUnit.MINUTES, 10),
            buildCache(DEMAND_LEADERS_CACHE, 60, TimeUnit.MINUTES, 20),
            buildCache(SALES_SUMMARY_CACHE, 60, TimeUnit.MINUTES, 10)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit, int maxSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}
