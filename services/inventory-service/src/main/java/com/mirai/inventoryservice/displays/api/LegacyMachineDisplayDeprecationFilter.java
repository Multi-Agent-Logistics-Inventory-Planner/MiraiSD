package com.mirai.inventoryservice.displays.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks the legacy machine-display routes (/api/machine-displays) as deprecated per
 * docs/baseline/api-v1-map.md (.specs/phase-7-displays T-4, AC-5), mirroring
 * {@code shipments.api.LegacyShipmentDeprecationFilter}'s exact header shapes (same omitted
 * Sunset -- no removal date decided yet, same slice deprecation date as shipments' since both
 * routes moved to v1 in the same phase).
 */
public class LegacyMachineDisplayDeprecationFilter extends OncePerRequestFilter {

    // RFC 9745 structured-fields Date for 2026-09-23T00:00:00Z, matching
    // shipments.api.LegacyShipmentDeprecationFilter.DEPRECATION_DATE (same phase, same date).
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
