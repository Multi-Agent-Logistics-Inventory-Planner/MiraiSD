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
import com.mirai.inventoryservice.identity.infrastructure.SiteAccessAuthorizationFilter;
import com.mirai.inventoryservice.shared.correlation.CorrelationIdContext;
import com.mirai.inventoryservice.shared.correlation.CorrelationIdFilter;
import com.mirai.inventoryservice.shared.correlation.IdempotencyKeyFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@AllArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final IdempotencyKeyFilter idempotencyKeyFilter;
    private final SiteAccessAuthorizationFilter siteAccessAuthorizationFilter;
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

                // OpenAPI docs (only actually registered when springdoc.api-docs.enabled=true,
                // i.e. the test profile - see application.properties)
                .requestMatchers("/v3/api-docs/**").permitAll()

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
        // Correlation ID runs before rate limiting so every subsequent filter's logs (and any
        // outbox events created during this request) can be tied back to the request.
        http.addFilterBefore(correlationIdFilter, RateLimitingFilter.class);
        // Idempotency key carries alongside correlation id; order relative to it doesn't matter
        // since neither reads the other, but placing it after keeps MDC setup together.
        http.addFilterAfter(idempotencyKeyFilter, CorrelationIdFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, RateLimitingFilter.class);
        // Runs after JWT auth so it can read the resolved AuthenticatedPrincipal, per
        // docs/specs/authentication-and-authorization.md section 5's resolution order.
        http.addFilterAfter(siteAccessAuthorizationFilter, JwtAuthenticationFilter.class);

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

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Idempotency-Key", CorrelationIdContext.HEADER_NAME));
        configuration.setExposedHeaders(Arrays.asList(CorrelationIdContext.HEADER_NAME));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}