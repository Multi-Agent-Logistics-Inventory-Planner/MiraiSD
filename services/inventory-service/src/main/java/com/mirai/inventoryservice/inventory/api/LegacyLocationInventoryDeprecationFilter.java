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
 * R-9's resolution) and {@code /api/storage-locations/{id}/inventory} -- and the legacy,
 * site-blind {@code GET /api/locations/with-counts} as deprecated, same header shapes as
 * {@link LegacyInventoryDeprecationFilter}.
 * <p>
 * (.specs/phase-6-inventory 6e, R-3 revert, 2026-09-15) The `/api/locations/{id}/inventory*`
 * routes were deleted in T-6e-be-9, this filter was narrowed to `with-counts` only in T-6e-be-8,
 * then both were reverted per the 6e independent review's R-3 finding -- the documented
 * compatibility-removal gate (docs/baseline/api-v1-map.md) needs access-log evidence plus a
 * stabilization window on a *released* version, unsatisfiable when this same filter's
 * deprecation headers for those exact routes only just landed on this same unmerged branch. This
 * filter now covers both route families again.
 * <p>
 * Registered at the wildcard {@code /api/locations/*} and {@code /api/storage-locations/*}
 * levels (Spring's {@code url-pattern} syntax cannot express a mid-path segment like {@code
 * /api/locations/{id}/inventory} directly, and `/api/locations/with-counts` is a literal
 * sibling), so this filter must itself gate on the request path -- otherwise {@code sites}' own
 * {@code LocationController} routes at plain {@code /api/locations/{id}} would be wrongly marked
 * deprecated too. Requests that don't match pass through untouched, with no headers added.
 */
public class LegacyLocationInventoryDeprecationFilter extends OncePerRequestFilter {

    static final String DEPRECATION_DATE = LegacyInventoryDeprecationFilter.DEPRECATION_DATE;
    static final String MIGRATION_DOC_LINK = LegacyInventoryDeprecationFilter.MIGRATION_DOC_LINK;

    private static final Pattern LOCATION_INVENTORY_PATH =
            Pattern.compile("^/api/locations/[^/]+/inventory(?:/.*)?$");
    private static final Pattern STORAGE_LOCATION_INVENTORY_PATH =
            Pattern.compile("^/api/storage-locations/[^/]+/inventory$");
    private static final String WITH_COUNTS_PATH = "/api/locations/with-counts";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null
                && (LOCATION_INVENTORY_PATH.matcher(path).matches()
                    || STORAGE_LOCATION_INVENTORY_PATH.matcher(path).matches()
                    || WITH_COUNTS_PATH.equals(path))) {
            response.setHeader("Deprecation", DEPRECATION_DATE);
            response.setHeader("Link", MIGRATION_DOC_LINK);
        }
        filterChain.doFilter(request, response);
    }
}
