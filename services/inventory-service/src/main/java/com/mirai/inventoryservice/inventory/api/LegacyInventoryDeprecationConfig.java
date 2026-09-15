package com.mirai.inventoryservice.inventory.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LegacyInventoryDeprecationFilter} only for the legacy global inventory/
 * stock-movement routes, leaving /api/v1/sites/{siteId}/inventory/** and every other route
 * untouched -- mirrors {@code catalog.api.LegacyCatalogDeprecationConfig}. Also registers
 * {@link LegacyLocationInventoryDeprecationFilter} for the legacy, site-blind
 * {@code GET /api/locations/with-counts} (.specs/phase-6-inventory 6e, T-6e-be-8; previously
 * this second filter targeted the now-deleted {@code /api/locations/{id}/inventory*}/
 * {@code /api/storage-locations/{id}/inventory} routes from 6d's T-6d-be-7) -- a separate
 * filter/registration because it must itself gate on the exact path, unlike this class's other
 * filter which is safe to apply to its entire registered prefix.
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

    @Bean
    public FilterRegistrationBean<LegacyLocationInventoryDeprecationFilter> legacyLocationInventoryDeprecationFilter() {
        FilterRegistrationBean<LegacyLocationInventoryDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyLocationInventoryDeprecationFilter());
        registration.addUrlPatterns("/api/locations/*");
        registration.setName("legacyLocationInventoryDeprecationFilter");
        return registration;
    }
}
