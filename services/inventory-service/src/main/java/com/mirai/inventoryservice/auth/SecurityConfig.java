package com.mirai.inventoryservice.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import lombok.AllArgsConstructor;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@AllArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF disabled for stateless JWT authentication
        // Requirements: ✅ JWT only, ✅ Proper CORS, ✅ SameSite cookies
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth ->
            auth
                // Public endpoints (no authentication required)
                .requestMatchers("/api/auth/validate", "/api/auth/session").permitAll()
                .requestMatchers("/health").permitAll()
                // Only expose health endpoint publicly; other actuator endpoints require ADMIN role
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Block dev endpoints in all profiles (defense in depth)
                .requestMatchers("/api/dev/**").denyAll()

                // Webhook endpoints (validated by signature, not JWT)
                .requestMatchers("/api/webhooks/**").permitAll()

                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
        );
  
        http.exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
            })
        );

        // Add rate limiting filter BEFORE JWT authentication
        // This ensures rate limiting happens first, protecting against DoS attacks
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, RateLimitingFilter.class);

        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Get additional allowed origins from environment variable (comma-separated)
        String additionalOrigins = environment.getProperty("cors.allowed.origins", "");

        // Default allowed origins: custom domain, Vercel deployments, and local development
        java.util.List<String> allowedOrigins = new java.util.ArrayList<>(Arrays.asList(
            "https://www.mirai-inventory.com",
            "https://mirai-inventory.com",
            "https://mirai-inventory.vercel.app",
            "https://mirai-inventory-felipes-projects-59edcd3e.vercel.app",
            "http://localhost:3000"
        ));

        // Add any additional origins from environment variable
        if (!additionalOrigins.isEmpty()) {
            Arrays.stream(additionalOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(allowedOrigins::add);
        }

        configuration.setAllowedOrigins(allowedOrigins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}