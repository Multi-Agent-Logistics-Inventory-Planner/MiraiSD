package com.mirai.inventoryservice.identity.application;

import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.application.SiteDirectory;
import com.mirai.inventoryservice.sites.application.SiteSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
