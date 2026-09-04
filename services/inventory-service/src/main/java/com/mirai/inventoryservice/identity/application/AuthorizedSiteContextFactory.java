package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContext;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only class that constructs an {@link AuthorizedSiteContext}, per its own Javadoc. Called by
 * {@code identity.infrastructure.SiteAccessAuthorizationFilter} for every
 * {@code /api/v1/sites/{siteId}/**} request, implementing the resolution order from
 * docs/specs/authentication-and-authorization.md section 5: authenticated user -> site exists ->
 * active membership (or an explicit, audited system-admin bypass) -> role-derived permissions.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class AuthorizedSiteContextFactory {

    private final MembershipAuthorizer membershipAuthorizer;
    private final SiteDirectory siteDirectory;

    public AuthorizedSiteContextFactory(MembershipAuthorizer membershipAuthorizer, SiteDirectory siteDirectory) {
        this.membershipAuthorizer = membershipAuthorizer;
        this.siteDirectory = siteDirectory;
    }

    public AuthorizationOutcome resolve(AuthenticatedPrincipal principal, UUID siteId, String correlationId) {
        if (principal == null || principal.backendUserId() == null) {
            return new AuthorizationOutcome.AccessDenied();
        }

        if (siteDirectory.findById(siteId).isEmpty()) {
            return new AuthorizationOutcome.SiteNotFound();
        }

        boolean hasActiveMembership = membershipAuthorizer.hasActiveMembership(principal.backendUserId(), siteId);
        boolean systemAdmin = principal.systemAdmin();

        if (!hasActiveMembership && !systemAdmin) {
            return new AuthorizationOutcome.AccessDenied();
        }

        if (!hasActiveMembership) {
            // Explicit, audited bypass - MUST NOT create a normal membership row (per section 5),
            // so this is the only trace of the bypass having happened.
            log.warn("System-admin bypass: user {} authorized for site {} with no active "
                            + "membership (correlationId={})",
                    principal.backendUserId(), siteId, correlationId);
        }

        Set<String> effectivePermissions = resolvePermissions(principal.role());

        AuthorizedSiteContext context = new AuthorizedSiteContext(
                principal.backendUserId(),
                siteId,
                principal.role(),
                effectivePermissions,
                systemAdmin,
                correlationId);

        return new AuthorizationOutcome.Authorized(context);
    }

    private static Set<String> resolvePermissions(String roleName) {
        if (roleName == null) {
            return Set.of();
        }
        try {
            UserRole role = UserRole.valueOf(roleName.toUpperCase());
            return RolePermissions.forRole(role).stream()
                    .map(Permission::key)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            return Set.of();
        }
    }
}
