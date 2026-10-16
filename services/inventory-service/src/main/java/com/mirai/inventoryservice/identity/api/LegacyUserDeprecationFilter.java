package com.mirai.inventoryservice.identity.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Marks the site-blind user list and last-audit reads as deprecated in favour of
 * {@code /api/v1/sites/{siteId}/users}, on error responses too. Id, email and name lookups and the
 * admin writes share the {@code /api/users} prefix and are not deprecated.
 */
public class LegacyUserDeprecationFilter extends OncePerRequestFilter {

    // RFC 9745 structured-fields Date for 2026-10-16T00:00:00Z.
    static final String DEPRECATION_DATE = "@1792108800";
    static final String MIGRATION_DOC_LINK =
            "<https://github.com/Multi-Agent-Logistics-Inventory-Planner/MiraiSD/blob/main/docs/baseline/api-v1-map.md>; rel=\"deprecation\"";

    private static final Pattern DEPRECATED_READS = Pattern.compile("^/api/users(/last-audits|/[^/]+/last-audit)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equals(request.getMethod()) && DEPRECATED_READS.matcher(request.getRequestURI()).matches()) {
            response.setHeader("Deprecation", DEPRECATION_DATE);
            response.setHeader("Link", MIGRATION_DOC_LINK);
        }
        filterChain.doFilter(request, response);
    }
}
