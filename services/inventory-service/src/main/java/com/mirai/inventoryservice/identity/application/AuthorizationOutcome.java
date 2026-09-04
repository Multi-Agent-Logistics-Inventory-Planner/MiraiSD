package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;

/**
 * Result of {@link AuthorizedSiteContextFactory#resolve}. A sealed hierarchy rather than a plain
 * {@code Optional} so callers (namely
 * {@code identity.infrastructure.SiteAccessAuthorizationFilter}) can distinguish "site does not
 * exist" (404) from "authenticated but not authorized for this site" (403), per
 * docs/specs/authentication-and-authorization.md section 5.
 */
public sealed interface AuthorizationOutcome
        permits AuthorizationOutcome.Authorized, AuthorizationOutcome.SiteNotFound, AuthorizationOutcome.AccessDenied {

    record Authorized(AuthorizedSiteContext context) implements AuthorizationOutcome {
    }

    record SiteNotFound() implements AuthorizationOutcome {
    }

    record AccessDenied() implements AuthorizationOutcome {
    }
}
