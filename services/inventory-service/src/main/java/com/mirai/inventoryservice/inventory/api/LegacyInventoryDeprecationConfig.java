package com.mirai.inventoryservice.inventory.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LegacyInventoryDeprecationFilter} only for the legacy global inventory/
 * stock-movement routes, leaving /api/v1/sites/{siteId}/inventory/** and every other route
 * untouched -- mirrors {@code catalog.api.LegacyCatalogDeprecationConfig}.
 */
@Configuration
public class LegacyInventoryDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyInventoryDeprecationFilter> legacyInventoryDeprecationFilter() {
        FilterRegistrationBean<LegacyInventoryDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyInventoryDeprecationFilter());
        registration.addUrlPatterns("/api/inventory/*", "/api/stock-movements/*");
        registration.setName("legacyInventoryDeprecationFilter");
        return registration;
    }
}
