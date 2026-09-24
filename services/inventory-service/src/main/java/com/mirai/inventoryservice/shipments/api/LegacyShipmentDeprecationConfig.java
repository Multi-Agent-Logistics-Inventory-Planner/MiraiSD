package com.mirai.inventoryservice.shipments.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LegacyShipmentDeprecationFilter} only for the legacy shipment/tracking/webhook
 * routes, leaving {@code /api/v1/sites/{siteId}/shipments/**},
 * {@code /api/v1/sites/{siteId}/tracking/**} and {@code /api/v1/webhooks/**} untouched -- mirrors
 * {@code inventory.api.LegacyInventoryDeprecationConfig}.
 */
@Configuration
public class LegacyShipmentDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyShipmentDeprecationFilter> legacyShipmentDeprecationFilter() {
        FilterRegistrationBean<LegacyShipmentDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyShipmentDeprecationFilter());
        // "/api/shipments" itself is a real legacy route (bare GET/POST, no trailing segment) --
        // the "/*" suffix pattern alone is not guaranteed by the servlet spec to also match the
        // exact parent path, so it's listed explicitly alongside the wildcard.
        registration.addUrlPatterns(
                "/api/shipments", "/api/shipments/*", "/api/tracking/*", "/api/webhooks/*");
        registration.setName("legacyShipmentDeprecationFilter");
        return registration;
    }
}
