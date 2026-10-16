package com.mirai.inventoryservice.catalog.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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
        // Must run before the security chain so 401/403 rejections carry the headers.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
