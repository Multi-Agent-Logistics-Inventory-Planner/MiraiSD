package com.mirai.inventoryservice.inventory.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks the legacy, site-blind {@code GET /api/locations/with-counts} as deprecated
 * (.specs/phase-6-inventory 6e, T-6e-be-8), same header shapes as
 * {@link LegacyInventoryDeprecationFilter}. The site-scoped v1 counterpart is
 * {@code GET /api/v1/sites/{siteId}/locations/with-counts}
 * ({@code sites.api.SiteLocationAggregateController}).
 * <p>
 * Registered at the wildcard {@code /api/locations/*} level, but gated internally on the exact
 * {@code /api/locations/with-counts} path so every other route under that prefix -- the legacy,
 * unscoped {@code controllers.LocationController}'s {@code /api/locations}/{@code
 * /api/locations/{id}} CRUD routes, untouched by this pass -- is left alone.
 * <p>
 * Previously named for, and gating on, the legacy sites-shaped {@code
 * /api/locations/{id}/inventory}/{@code /api/storage-locations/{id}/inventory} routes
 * (.specs/phase-6-inventory 6d, T-6d-be-7, R-9); those routes and their controller were deleted
 * in 6e (T-6e-be-9), retiring that match entirely.
 */
public class LegacyLocationInventoryDeprecationFilter extends OncePerRequestFilter {

    static final String DEPRECATION_DATE = LegacyInventoryDeprecationFilter.DEPRECATION_DATE;
    static final String MIGRATION_DOC_LINK = LegacyInventoryDeprecationFilter.MIGRATION_DOC_LINK;

    private static final String WITH_COUNTS_PATH = "/api/locations/with-counts";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (WITH_COUNTS_PATH.equals(request.getRequestURI())) {
            response.setHeader("Deprecation", DEPRECATION_DATE);
            response.setHeader("Link", MIGRATION_DOC_LINK);
        }
        filterChain.doFilter(request, response);
    }
}
