package com.mirai.inventoryservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the generated OpenAPI contract (packages/contracts/openapi.json) deterministic and
 * accurate to what SecurityConfig actually enforces, since the client in packages/api-client is
 * generated straight from it.
 */
@Configuration
public class OpenApiConfig {

    // Mirrors SecurityConfig's permitAll matchers. Kept here rather than shared with
    // SecurityConfig because this only affects documentation, not enforcement.
    private static final List<String> PUBLIC_PATH_PATTERNS = List.of(
            "/api/auth/validate", "/api/auth/session",
            "/health", "/actuator/health", "/actuator/health/**",
            "/api/webhooks/**", "/v3/api-docs/**");

    @Bean
    public OpenAPI openApi() {
        // A fixed relative server URL instead of springdoc's default (which embeds the
        // request's host:port - random in tests) keeps the generated contract deterministic
        // across every run, local or CI.
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public GlobalOpenApiCustomizer authDocumentationCustomizer() {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        return openApi -> openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> {
                    if (isPublic(path, pathMatcher)) {
                        return;
                    }
                    operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                    addErrorResponse(operation, "401", "Authentication required");
                    addErrorResponse(operation, "403", "Insufficient permissions");
                }));
    }

    private static boolean isPublic(String path, AntPathMatcher pathMatcher) {
        return PUBLIC_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static void addErrorResponse(Operation operation, String status, String message) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("status", new Schema<>().type("integer"));
        properties.put("error", new Schema<>().type("string"));
        properties.put("message", new Schema<>().type("string"));
        Schema<?> errorSchema = new Schema<>().type("object").properties(properties);
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(message)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(errorSchema))));
    }
}
