package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the admin membership-lifecycle endpoints' role gates, not-found handling, and list
 * behavior - per docs/plans/enterprise-modernization.md Phase 4's last outstanding item. The
 * grant success path and revoke-then-grant reactivation round trip are covered separately in
 * {@code UserSiteMembershipControllerKafkaIT}: granting goes through
 * {@code UserSiteMembershipRepository#insertActiveIfAbsent}'s native {@code ON CONFLICT} clause,
 * which H2 (this class's database) doesn't support outside Postgres-compatibility mode - the
 * same reason {@code UserSiteMembershipRepositoryIT} needs real Postgres.
 */
class UserSiteMembershipControllerIT extends BaseIntegrationTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSiteMembershipRepository userSiteMembershipRepository;

    private User createTargetUser(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Target User").role(UserRole.EMPLOYEE).build());
    }

    @Test
    void grantReturns403ForNonAdmin() throws Exception {
        String token = assistantManagerToken();
        User target = createTargetUser("target-1@test.internal");
        Site site = siteRepository.save(Site.builder().name("Site A").code("USM-1").build());

        mockMvc.perform(put("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantReturns404ForAnUnknownSite() throws Exception {
        String token = adminToken();
        User target = createTargetUser("target-3@test.internal");

        mockMvc.perform(put("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void grantReturns404ForAnUnknownUser() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Site C").code("USM-3").build());

        mockMvc.perform(put("/api/admin/users/{userId}/site-memberships/{siteId}", UUID.randomUUID(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeReturns204ForAnExistingMembership() throws Exception {
        String token = adminToken();
        User target = createTargetUser("target-4@test.internal");
        Site site = siteRepository.save(Site.builder().name("Site D").code("USM-4").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(target.getId()).siteId(site.getId()).isActive(true).build());

        mockMvc.perform(delete("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertFalse(userSiteMembershipRepository.existsByUserIdAndSiteIdAndIsActiveTrue(target.getId(), site.getId()));
    }

    @Test
    void revokeReturns404WhenNoMembershipExists() throws Exception {
        String token = adminToken();
        User target = createTargetUser("target-5@test.internal");
        Site site = siteRepository.save(Site.builder().name("Site E").code("USM-5").build());

        mockMvc.perform(delete("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturns403ForNonAdmin() throws Exception {
        // /api/admin/** is ADMIN-only at the URL-matcher level (SecurityConfig), so this covers
        // both EMPLOYEE and ASSISTANT_MANAGER - neither can reach this endpoint.
        String token = assistantManagerToken();
        User target = createTargetUser("target-6@test.internal");

        mockMvc.perform(get("/api/admin/users/{userId}/site-memberships", target.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIncludesBothActiveAndInactiveMemberships() throws Exception {
        String token = adminToken();
        User target = createTargetUser("target-7@test.internal");
        Site activeSite = siteRepository.save(Site.builder().name("Site F").code("USM-6").build());
        Site inactiveSite = siteRepository.save(Site.builder().name("Site G").code("USM-7").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(target.getId()).siteId(activeSite.getId()).isActive(true).build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(target.getId()).siteId(inactiveSite.getId()).isActive(false).build());

        mockMvc.perform(get("/api/admin/users/{userId}/site-memberships", target.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
