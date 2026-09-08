package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.application.SiteSummary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves MAIN's site id for catalog's legacy-compatibility dual-write (.specs/
 * phase-5c-site-products AC-5). {@code catalog.application} depends directly on {@code
 * sites.application.SiteDirectory} here, per docs/specs/spring-domain-modular-monolith.md rule
 * 6.1.2 ("application MAY depend on ... another module's documented application facade") - never
 * {@code sites.infrastructure} directly. Public (and non-static) so Mockito can stand it in for
 * {@link ProductService}'s legacy-compatibility unit tests from outside this package.
 */
@Component
public class MainSiteResolver {

    private static final String MAIN_SITE_CODE = "MAIN";

    private final SiteDirectory siteDirectory;

    public MainSiteResolver(SiteDirectory siteDirectory) {
        this.siteDirectory = siteDirectory;
    }

    /**
     * @throws IllegalStateException if no MAIN site row exists - an invariant every production
     *     deployment satisfies (V57 refuses to run without one); callers that must act on MAIN
     *     specifically (legacy product writes) fail loudly rather than silently skipping.
     */
    public UUID resolve() {
        return siteDirectory.findByCode(MAIN_SITE_CODE)
                .map(SiteSummary::id)
                .orElseThrow(() -> new IllegalStateException(
                        "MAIN site not found - required for legacy product compatibility"));
    }

    /**
     * {@code false} (never throws) when MAIN does not exist - unlike {@link #resolve()}, callers
     * here are checking an arbitrary site against MAIN, not acting on MAIN itself, so "MAIN
     * doesn't exist yet" correctly means "this isn't MAIN," not a fatal error.
     */
    public boolean isMain(UUID siteId) {
        return siteDirectory.findByCode(MAIN_SITE_CODE)
                .map(summary -> summary.id().equals(siteId))
                .orElse(false);
    }
}
