package com.mirai.inventoryservice.shared.web;

import java.util.Optional;

/**
 * Per-request holder for the {@link AuthorizedSiteContext} resolved by
 * {@code identity.infrastructure.SiteAccessAuthorizationFilter}, modeled on
 * {@code shared.correlation.CorrelationIdContext}. {@code set}/{@code clear} are public rather
 * than package-private (Java visibility can't span the {@code shared.web} and
 * {@code identity.infrastructure} packages) - only the filter should call them, matching how
 * {@code CorrelationIdFilter} writes to its context with no separate writer restriction either.
 */
public final class AuthorizedSiteContextHolder {

    private static final ThreadLocal<AuthorizedSiteContext> CURRENT = new ThreadLocal<>();

    private AuthorizedSiteContextHolder() {
    }

    /** The current request's resolved site context, or empty outside a site-scoped request. */
    public static Optional<AuthorizedSiteContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * @throws IllegalStateException if no context has been resolved for this request - only call
     *         from a controller mapped under a path the filter guarantees resolution for, e.g.
     *         {@code /api/v1/sites/{siteId}/**}.
     */
    public static AuthorizedSiteContext require() {
        return current().orElseThrow(() ->
                new IllegalStateException("No AuthorizedSiteContext resolved for this request"));
    }

    /** Only {@code SiteAccessAuthorizationFilter} should call this. */
    public static void set(AuthorizedSiteContext context) {
        CURRENT.set(context);
    }

    /** Only {@code SiteAccessAuthorizationFilter} should call this, from a finally block. */
    public static void clear() {
        CURRENT.remove();
    }
}
