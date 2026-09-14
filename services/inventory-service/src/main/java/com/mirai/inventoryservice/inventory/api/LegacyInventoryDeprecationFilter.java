package com.mirai.inventoryservice.inventory.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks the legacy global inventory/stock-movement routes (/api/inventory, /api/stock-movements)
 * as deprecated per multi-site-data-and-api.md:126-128 (.specs/phase-6-inventory 6c, T-6c-13),
 * mirroring {@code catalog.api.LegacyCatalogDeprecationFilter} exactly -- same header shapes, same
 * omitted Sunset (no removal date decided yet). {@code /api/locations/{id}/inventory} is
 * sites-shaped legacy routing and is deliberately left alone this pass (out of scope per the task
 * list), so this filter is registered only for /api/inventory/** and /api/stock-movements/**.
 *
 * <p>The Deprecation header's value is a structured-fields Date (RFC 8941), written
 * {@code @<unix-timestamp>} per RFC 9745 - not an HTTP-date string.
 */
public class LegacyInventoryDeprecationFilter extends OncePerRequestFilter {

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
