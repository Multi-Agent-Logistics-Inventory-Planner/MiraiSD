package com.mirai.inventoryservice.audit.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers {@link LegacyAuditDeprecationFilter} for the legacy audit-log routes only. */
@Configuration
public class LegacyAuditDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyAuditDeprecationFilter> legacyAuditDeprecationFilter() {
        FilterRegistrationBean<LegacyAuditDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyAuditDeprecationFilter());
        registration.addUrlPatterns("/api/audit-logs", "/api/audit-logs/*");
        registration.setName("legacyAuditDeprecationFilter");
        return registration;
    }
}
