package com.mirai.inventoryservice.audit.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers {@link LegacyAuditDeprecationFilter} for the legacy audit-log routes only. */
@Configuration
public class LegacyAuditDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyAuditDeprecationFilter> legacyAuditDeprecationFilter() {
        FilterRegistrationBean<LegacyAuditDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyAuditDeprecationFilter());
        registration.addUrlPatterns("/api/audit-logs", "/api/audit-logs/*");
        registration.setName("legacyAuditDeprecationFilter");
        // Must run before the security chain so 401/403 rejections carry the headers.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
