package com.mirai.inventoryservice.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

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

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/dev/**")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/dev/**").permitAll()
            );

        return http.build();
    }

    /**
     * Temporary: allow /api/lootbox/** without auth in dev mode so the feature can be
     * exercised end-to-end without a Supabase JWT mapping to a local user. The lootbox
     * controllers fall back to the first ADMIN user in the local DB when no principal
     * is present. Remove this bean (or its securityMatcher) once you're ready to test
     * against real JWT-mapped users.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain devLootboxFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/lootbox/**")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/lootbox/**").permitAll()
            );

        return http.build();
    }
}
