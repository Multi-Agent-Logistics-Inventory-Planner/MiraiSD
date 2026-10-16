package com.mirai.inventoryservice.reviews.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks the legacy {@code /api/reviews} routes as deprecated in favour of
 * {@code /api/v1/sites/{siteId}/reviews}, with the same header shapes as the other legacy-route
 * filters (no Sunset: no removal date is decided yet).
 */
public class LegacyReviewDeprecationFilter extends OncePerRequestFilter {

    // RFC 9745 structured-fields Date for 2026-10-15T00:00:00Z.
    static final String DEPRECATION_DATE = "@1792022400";
    static final String MIGRATION_DOC_LINK =
            "<https://github.com/Multi-Agent-Logistics-Inventory-Planner/MiraiSD/blob/main/docs/baseline/api-v1-map.md>; rel=\"deprecation\"";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Deprecation", DEPRECATION_DATE);
        response.setHeader("Link", MIGRATION_DOC_LINK);
        filterChain.doFilter(request, response);
    }
}
