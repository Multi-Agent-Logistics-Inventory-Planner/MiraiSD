package com.mirai.inventoryservice.identity.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers {@link LegacyUserDeprecationFilter} for the legacy user read routes. */
@Configuration
public class LegacyUserDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyUserDeprecationFilter> legacyUserDeprecationFilter() {
        FilterRegistrationBean<LegacyUserDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyUserDeprecationFilter());
        registration.addUrlPatterns("/api/users", "/api/users/*");
        registration.setName("legacyUserDeprecationFilter");
        // Must run before the security chain so 401/403 rejections carry the headers.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
