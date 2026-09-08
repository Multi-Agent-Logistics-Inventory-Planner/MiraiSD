package com.mirai.inventoryservice.catalog.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LegacyCatalogDeprecationFilter} only for the legacy global catalog routes,
 * leaving /api/v1/catalog/** and every other route untouched.
 */
@Configuration
public class LegacyCatalogDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyCatalogDeprecationFilter> legacyCatalogDeprecationFilter() {
        FilterRegistrationBean<LegacyCatalogDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyCatalogDeprecationFilter());
        registration.addUrlPatterns("/api/products/*", "/api/categories/*", "/api/suppliers/*");
        registration.setName("legacyCatalogDeprecationFilter");
        return registration;
    }
}
