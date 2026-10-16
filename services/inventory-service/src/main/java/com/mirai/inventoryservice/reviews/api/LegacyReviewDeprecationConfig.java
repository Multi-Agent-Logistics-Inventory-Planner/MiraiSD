package com.mirai.inventoryservice.reviews.api;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers {@link LegacyReviewDeprecationFilter} for the legacy review routes only. */
@Configuration
public class LegacyReviewDeprecationConfig {

    @Bean
    public FilterRegistrationBean<LegacyReviewDeprecationFilter> legacyReviewDeprecationFilter() {
        FilterRegistrationBean<LegacyReviewDeprecationFilter> registration =
                new FilterRegistrationBean<>(new LegacyReviewDeprecationFilter());
        registration.addUrlPatterns("/api/reviews", "/api/reviews/*");
        registration.setName("legacyReviewDeprecationFilter");
        return registration;
    }
}
