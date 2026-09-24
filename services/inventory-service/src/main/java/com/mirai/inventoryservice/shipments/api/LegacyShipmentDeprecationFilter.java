package com.mirai.inventoryservice.shipments.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks the legacy shipment/tracking/webhook routes (/api/shipments, /api/tracking,
 * /api/webhooks) as deprecated per docs/baseline/api-v1-map.md (.specs/phase-7-shipments T-5,
 * AC-5), mirroring {@code inventory.api.LegacyInventoryDeprecationFilter}'s exact header shapes
 * (same omitted Sunset -- no removal date decided yet). {@code /api/webhooks/easypost} also gets
 * these headers even though it isn't a web-consumer-facing route: EasyPost itself ignores
 * unrecognized response headers, and marking it consistently with the rest of this slice's legacy
 * routes keeps docs/baseline/api-v1-map.md's compatibility-removal gate uniform across the three
 * families this filter covers.
 */
public class LegacyShipmentDeprecationFilter extends OncePerRequestFilter {

    // RFC 9745 structured-fields Date for 2026-09-23T00:00:00Z (this slice's deprecation date),
    // not inventory.api.LegacyInventoryDeprecationFilter's @1788825600 (that filter's own,
    // earlier phase-6c date).
    static final String DEPRECATION_DATE = "@1790121600";
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
