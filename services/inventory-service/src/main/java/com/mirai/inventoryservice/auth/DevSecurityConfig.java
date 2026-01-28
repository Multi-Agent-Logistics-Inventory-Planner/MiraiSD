package com.mirai.inventoryservice.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for development endpoints.
 * Only active when the "dev" profile is enabled.
 * This allows /api/dev/** endpoints without authentication in dev mode only.
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
@Order(1)
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/dev/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/dev/**").permitAll()
            );

        return http.build();
    }
}
