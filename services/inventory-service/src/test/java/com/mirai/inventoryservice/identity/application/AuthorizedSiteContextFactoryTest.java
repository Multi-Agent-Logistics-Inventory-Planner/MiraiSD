package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.AuthenticatedPrincipal;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.application.SiteSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizedSiteContextFactoryTest {

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private SiteDirectory siteDirectory;

    private AuthorizedSiteContextFactory factory;

    private final UUID userId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final SiteSummary site = new SiteSummary(siteId, "Site", "SITE");

    @Test
    void unauthenticatedPrincipalIsAccessDenied() {
        factory = new AuthorizedSiteContextFactory(membershipAuthorizer, siteDirectory);

        AuthorizationOutcome outcome = factory.resolve(null, siteId, "corr-1");

        assertThat(outcome).isInstanceOf(AuthorizationOutcome.AccessDenied.class);
    }

    @Test
    void unknownSiteIsSiteNotFound() {
        factory = new AuthorizedSiteContextFactory(membershipAuthorizer, siteDirectory);
        when(siteDirectory.findById(siteId)).thenReturn(Optional.empty());
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(), userId, "a@test.internal", "A", "ADMIN", false);

        AuthorizationOutcome outcome = factory.resolve(principal, siteId, "corr-1");

        assertThat(outcome).isInstanceOf(AuthorizationOutcome.SiteNotFound.class);
    }

    @Test
    void absentMembershipIsAccessDeniedForNonSystemAdmin() {
        factory = new AuthorizedSiteContextFactory(membershipAuthorizer, siteDirectory);
        when(siteDirectory.findById(siteId)).thenReturn(Optional.of(site));
        when(membershipAuthorizer.hasActiveMembership(userId, siteId)).thenReturn(false);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(), userId, "a@test.internal", "A", "ADMIN", false);

        AuthorizationOutcome outcome = factory.resolve(principal, siteId, "corr-1");

        assertThat(outcome).isInstanceOf(AuthorizationOutcome.AccessDenied.class);
    }

    @Test
    void activeMembershipIsAuthorizedWithRolePermissions() {
        factory = new AuthorizedSiteContextFactory(membershipAuthorizer, siteDirectory);
        when(siteDirectory.findById(siteId)).thenReturn(Optional.of(site));
        when(membershipAuthorizer.hasActiveMembership(userId, siteId)).thenReturn(true);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(), userId, "a@test.internal", "A", "ADMIN", false);

        AuthorizationOutcome outcome = factory.resolve(principal, siteId, "corr-1");

        assertThat(outcome).isInstanceOf(AuthorizationOutcome.Authorized.class);
        var authorized = (AuthorizationOutcome.Authorized) outcome;
        assertThat(authorized.context().effectivePermissions()).contains("costs:view");
        assertThat(authorized.context().systemAdmin()).isFalse();
    }

    @Test
    void systemAdminBypassesAbsentMembership() {
        factory = new AuthorizedSiteContextFactory(membershipAuthorizer, siteDirectory);
        when(siteDirectory.findById(siteId)).thenReturn(Optional.of(site));
        when(membershipAuthorizer.hasActiveMembership(userId, siteId)).thenReturn(false);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(), userId, "a@test.internal", "A", "EMPLOYEE", true);

        AuthorizationOutcome outcome = factory.resolve(principal, siteId, "corr-1");

        assertThat(outcome).isInstanceOf(AuthorizationOutcome.Authorized.class);
        assertThat(((AuthorizationOutcome.Authorized) outcome).context().systemAdmin()).isTrue();
    }
}
