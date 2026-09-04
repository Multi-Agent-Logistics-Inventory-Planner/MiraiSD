package com.mirai.inventoryservice.identity.infrastructure;

import com.mirai.inventoryservice.identity.application.AuthorizationOutcome;
import com.mirai.inventoryservice.identity.application.AuthorizedSiteContextFactory;
import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.shared.correlation.CorrelationIdContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Authorizes every {@code /api/v1/sites/{siteId}/**} request against the caller's site
 * membership, resolving and stashing an {@code AuthorizedSiteContext} for downstream controllers
 * to read via {@link AuthorizedSiteContextHolder}. Runs after {@code JwtAuthenticationFilter} (so
 * an {@link AuthenticatedPrincipal} is already on the security context) and mirrors its
 * filter/holder shape rather than a {@code HandlerMethodArgumentResolver}, so membership failures
 * reuse the same 401/403 JSON error shape as {@code SecurityConfig}'s exception handling.
 */
@Component
public class SiteAccessAuthorizationFilter extends OncePerRequestFilter {

    private static final String SITE_SCOPED_PATTERN = "/api/v1/sites/{siteId}/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AuthorizedSiteContextFactory authorizedSiteContextFactory;

    public SiteAccessAuthorizationFilter(AuthorizedSiteContextFactory authorizedSiteContextFactory) {
        this.authorizedSiteContextFactory = authorizedSiteContextFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!pathMatcher.match(SITE_SCOPED_PATTERN, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, String> variables = pathMatcher.extractUriTemplateVariables(SITE_SCOPED_PATTERN, path);
        UUID siteId = parseUuid(variables.get("siteId"));
        if (siteId == null) {
            writeError(response, 404, "Not Found", "Site not found");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedPrincipal principal = authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedPrincipal resolved
                ? resolved
                : null;

        if (principal == null) {
            // No JWT, or an invalid/expired one: JwtAuthenticationFilter left the security
            // context empty, so this is "who are you", not "you can't have this" - let the
            // request continue to Spring Security's anyRequest().authenticated() check, which
            // rejects it with 401 via SecurityConfig's authenticationEntryPoint. Resolving a
            // AuthorizationOutcome here would misreport that as 403, per
            // docs/specs/authentication-and-authorization.md's malformed/invalid-token -> 401
            // rule; 403 is reserved for an authenticated principal without site access.
            filterChain.doFilter(request, response);
            return;
        }

        AuthorizationOutcome outcome = authorizedSiteContextFactory.resolve(
                principal, siteId, CorrelationIdContext.current());

        try {
            switch (outcome) {
                case AuthorizationOutcome.Authorized authorized -> {
                    AuthorizedSiteContextHolder.set(authorized.context());
                    filterChain.doFilter(request, response);
                }
                case AuthorizationOutcome.SiteNotFound ignored ->
                        writeError(response, 404, "Not Found", "Site not found");
                case AuthorizationOutcome.AccessDenied ignored ->
                        writeError(response, 403, "Forbidden", "Insufficient permissions");
            }
        } finally {
            // Thread-pool reuse means a stale context must never leak into the next request,
            // per the CorrelationIdFilter precedent this class mirrors.
            AuthorizedSiteContextHolder.clear();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void writeError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
