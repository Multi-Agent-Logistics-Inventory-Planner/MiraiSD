package com.mirai.inventoryservice.shared.web;

import java.util.Set;
import java.util.UUID;

/**
 * Trusted result of resolving an authenticated request against a specific site, per
 * docs/specs/authentication-and-authorization.md section 5. Effective permissions are carried as
 * permission keys ({@code Set<String>}, e.g. "products:view"), not the {@code identity.domain}
 * enum: {@code shared} must not depend on a business module
 * (docs/specs/spring-domain-modular-monolith.md section 6, enforced by
 * ArchitectureTest#sharedDoesNotDependOnBusinessModules).
 * <p>
 * Java gives a public record no narrower a canonical constructor than the record itself, so this
 * cannot be compiler-enforced; by convention, only
 * {@code identity.application.AuthorizedSiteContextFactory} constructs one, after real
 * authentication and membership resolution. Every other module (e.g. {@code sites} controllers)
 * must only read an already-resolved context off {@link AuthorizedSiteContextHolder}, never
 * construct one directly.
 */
public record AuthorizedSiteContext(
        UUID backendUserId,
        UUID siteId,
        String role,
        Set<String> effectivePermissions,
        boolean systemAdmin,
        String correlationId) {
}
