package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.shared.correlation.CorrelationIdContext;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Trusted compatibility adapter for retained unversioned, site-owned HTTP routes.  Legacy URLs
 * intentionally continue to mean MAIN during the migration, but MAIN is never an authorization
 * bypass: the caller still needs an active MAIN membership (or the existing explicit system-admin
 * bypass) and controllers must use the returned context for all resource lookup and audit actor
 * values.
 */
@Component
public class LegacyMainSiteContextResolver {
    private final AuthorizedSiteContextFactory contextFactory;
    private final SiteDirectory siteDirectory;

    public LegacyMainSiteContextResolver(
            AuthorizedSiteContextFactory contextFactory,
            SiteDirectory siteDirectory) {
        this.contextFactory = contextFactory;
        this.siteDirectory = siteDirectory;
    }

    public AuthorizedSiteContext requireMain() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedPrincipal principal = authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedPrincipal resolved
                ? resolved
                : null;
        if (principal == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        var main = siteDirectory.findByCode(LocationService.DEFAULT_SITE_CODE)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Site not found"));
        AuthorizationOutcome outcome = contextFactory.resolve(
                principal, main.id(), CorrelationIdContext.current());
        if (outcome instanceof AuthorizationOutcome.Authorized authorized) {
            return authorized.context();
        }
        if (outcome instanceof AuthorizationOutcome.SiteNotFound) {
            throw new ResponseStatusException(NOT_FOUND, "Site not found");
        }
        throw new AccessDeniedException("Insufficient permissions");
    }
}
