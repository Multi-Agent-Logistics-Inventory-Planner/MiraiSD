package com.mirai.inventoryservice.catalog.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks the legacy global catalog routes (/api/products, /api/categories, /api/suppliers) as
 * deprecated per multi-site-data-and-api.md section 7, without touching their response bodies.
 * Sunset is intentionally omitted: no removal date has been decided yet.
 *
 * <p>The Deprecation header's value is a structured-fields Date (RFC 8941), written
 * {@code @<unix-timestamp>} per RFC 9745 - not an HTTP-date string.
 */
public class LegacyCatalogDeprecationFilter extends OncePerRequestFilter {

    static final String DEPRECATION_DATE = "@1788825600";
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
