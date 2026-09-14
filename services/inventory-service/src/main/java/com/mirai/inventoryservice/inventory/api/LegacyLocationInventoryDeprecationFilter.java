package com.mirai.inventoryservice.inventory.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Marks the legacy sites-shaped location-inventory routes -- {@code
 * /api/locations/{id}/inventory} (list/create/item-get/item-delete; the item-PUT is dropped per
 * R-9's resolution) and {@code /api/storage-locations/{id}/inventory} -- as deprecated
 * (.specs/phase-6-inventory 6d, T-6d-be-7, R-9), same header shapes as
 * {@link LegacyInventoryDeprecationFilter}.
 * <p>
 * Registered at the wildcard {@code /api/locations/*} and {@code /api/storage-locations/*} levels
 * (Spring's {@code url-pattern} syntax cannot express a mid-path segment like {@code
 * /api/locations/{id}/inventory} directly), so this filter must itself gate on the request path
 * actually ending in an {@code inventory} segment -- otherwise {@code sites}' own {@code
 * LocationController} routes at plain {@code /api/locations/{id}} would be wrongly marked
 * deprecated too. Requests that don't match pass through untouched, with no headers added.
 */
public class LegacyLocationInventoryDeprecationFilter extends OncePerRequestFilter {

    static final String DEPRECATION_DATE = LegacyInventoryDeprecationFilter.DEPRECATION_DATE;
    static final String MIGRATION_DOC_LINK = LegacyInventoryDeprecationFilter.MIGRATION_DOC_LINK;

    private static final Pattern LOCATION_INVENTORY_PATH =
            Pattern.compile("^/api/locations/[^/]+/inventory(?:/.*)?$");
    private static final Pattern STORAGE_LOCATION_INVENTORY_PATH =
            Pattern.compile("^/api/storage-locations/[^/]+/inventory$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null
                && (LOCATION_INVENTORY_PATH.matcher(path).matches()
                    || STORAGE_LOCATION_INVENTORY_PATH.matcher(path).matches())) {
            response.setHeader("Deprecation", DEPRECATION_DATE);
            response.setHeader("Link", MIGRATION_DOC_LINK);
        }
        filterChain.doFilter(request, response);
    }
}
