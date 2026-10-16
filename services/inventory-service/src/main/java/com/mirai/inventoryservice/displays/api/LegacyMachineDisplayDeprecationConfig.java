package com.mirai.inventoryservice.displays.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link LegacyMachineDisplayDeprecationFilter} only for the legacy machine-display
 * routes, leaving {@code /api/v1/sites/{siteId}/machine-displays/**} untouched -- mirrors
 * {@code shipments.api.LegacyShipmentDeprecationConfig}.
 */
@Configuration
public class LegacyMachineDisplayDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyMachineDisplayDeprecationFilter> legacyMachineDisplayDeprecationFilter() {
        FilterRegistrationBean<LegacyMachineDisplayDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyMachineDisplayDeprecationFilter());
        // "/api/machine-displays" itself is a real legacy route (bare GET/POST, no trailing
        // segment) -- the "/*" suffix pattern alone is not guaranteed by the servlet spec to also
        // match the exact parent path, so it's listed explicitly alongside the wildcard.
        registration.addUrlPatterns("/api/machine-displays", "/api/machine-displays/*");
        registration.setName("legacyMachineDisplayDeprecationFilter");
        // Must run before the security chain so 401/403 rejections carry the headers.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
