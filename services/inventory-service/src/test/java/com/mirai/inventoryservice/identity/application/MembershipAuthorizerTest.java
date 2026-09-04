package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.domain.MembershipNotFoundException;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.application.SiteSummary;
import com.mirai.inventoryservice.sites.domain.SiteNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipAuthorizerTest {

    @Mock
    private UserSiteMembershipRepository userSiteMembershipRepository;

    @Mock
    private SiteDirectory siteDirectory;

    private MembershipAuthorizer membershipAuthorizer;

    private final UUID userId = UUID.randomUUID();
    private final UUID mainSiteId = UUID.randomUUID();

    @Test
    void grantMainSiteMembershipIfAbsentDelegatesToTheAtomicUpsert() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        when(siteDirectory.allSites()).thenReturn(List.of(new SiteSummary(mainSiteId, "Main", "MAIN")));

        membershipAuthorizer.grantMainSiteMembershipIfAbsent(userId);

        // Concurrency safety (no race between "check" and "insert") comes from the database's
        // unique constraint via this atomic call, not from application-level find-then-save -
        // see UserSiteMembershipRepository#insertActiveIfAbsent.
        verify(userSiteMembershipRepository).insertActiveIfAbsent(userId, mainSiteId);
    }

    @Test
    void grantMainSiteMembershipIfAbsentDoesNothingWhenNoMainSiteExists() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        when(siteDirectory.allSites()).thenReturn(List.of());

        membershipAuthorizer.grantMainSiteMembershipIfAbsent(userId);

        verifyNoInteractions(userSiteMembershipRepository);
    }

    @Test
    void hasActiveMembershipReturnsFalseForNullArguments() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);

        assertThat(membershipAuthorizer.hasActiveMembership(null, mainSiteId)).isFalse();
        assertThat(membershipAuthorizer.hasActiveMembership(userId, null)).isFalse();
    }

    @Test
    void grantMembershipInsertsThenActivates() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        UUID siteId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(siteDirectory.findById(siteId)).thenReturn(Optional.of(new SiteSummary(siteId, "Second", "SECOND")));

        membershipAuthorizer.grantMembership(actorId, userId, siteId);

        // insertActiveIfAbsent alone cannot reactivate a previously revoked row (it's a no-op on
        // conflict, not a true upsert), so grant must also call activate unconditionally.
        verify(userSiteMembershipRepository).insertActiveIfAbsent(userId, siteId);
        verify(userSiteMembershipRepository).activate(any(), any(), any());
    }

    @Test
    void grantMembershipThrowsSiteNotFoundForAnUnknownSite() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        UUID siteId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(siteDirectory.findById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipAuthorizer.grantMembership(actorId, userId, siteId))
                .isInstanceOf(SiteNotFoundException.class);

        verify(userSiteMembershipRepository, never()).insertActiveIfAbsent(any(), any());
        verify(userSiteMembershipRepository, never()).activate(any(), any(), any());
    }

    @Test
    void revokeMembershipDeactivatesWhenARowExists() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        UUID siteId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(userSiteMembershipRepository.deactivate(any(), any(), any())).thenReturn(1);

        membershipAuthorizer.revokeMembership(actorId, userId, siteId);

        verify(userSiteMembershipRepository).deactivate(any(), any(), any());
    }

    @Test
    void revokeMembershipThrowsWhenNoMembershipRowExists() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        UUID siteId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(userSiteMembershipRepository.deactivate(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> membershipAuthorizer.revokeMembership(actorId, userId, siteId))
                .isInstanceOf(MembershipNotFoundException.class);
    }

    @Test
    void membershipsForMapsActiveAndInactiveRows() {
        membershipAuthorizer = new MembershipAuthorizer(userSiteMembershipRepository, siteDirectory);
        UUID activeSiteId = UUID.randomUUID();
        UUID inactiveSiteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        when(userSiteMembershipRepository.findByUserId(userId)).thenReturn(List.of(
                UserSiteMembership.builder().userId(userId).siteId(activeSiteId).isActive(true).updatedAt(now).build(),
                UserSiteMembership.builder().userId(userId).siteId(inactiveSiteId).isActive(false).updatedAt(now).build()));

        List<MembershipStatus> result = membershipAuthorizer.membershipsFor(userId);

        assertThat(result).containsExactlyInAnyOrder(
                new MembershipStatus(activeSiteId, true, now),
                new MembershipStatus(inactiveSiteId, false, now));
    }
}
