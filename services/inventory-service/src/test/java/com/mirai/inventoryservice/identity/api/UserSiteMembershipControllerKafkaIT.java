package com.mirai.inventoryservice.identity.api;

import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the grant success path and revoke-then-grant reactivation round trip for
 * {@code UserSiteMembershipController} - split out from {@code UserSiteMembershipControllerIT}
 * because granting goes through {@code UserSiteMembershipRepository#insertActiveIfAbsent}'s
 * native {@code ON CONFLICT} clause, which needs real Postgres semantics H2 doesn't provide
 * (see {@code UserSiteMembershipRepositoryIT}, which needs Postgres for the same reason).
 */
class UserSiteMembershipControllerKafkaIT extends BaseKafkaIntegrationTest {

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
    void grantReturns200ForAdminAndCreatesAnActiveMembership() throws Exception {
        String token = adminToken();
        User target = createTargetUser("target-kafka-1@test.internal");
        Site site = siteRepository.save(Site.builder().name("Site A").code("USM-K1").build());

        mockMvc.perform(put("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value(site.getId().toString()))
                .andExpect(jsonPath("$.active").value(true));

        assertTrue(userSiteMembershipRepository.existsByUserIdAndSiteIdAndIsActiveTrue(target.getId(), site.getId()));
    }

    @Test
    void revokeThenGrantReactivatesTheSameRow() throws Exception {
        String token = adminToken();
        User target = createTargetUser("target-kafka-2@test.internal");
        Site site = siteRepository.save(Site.builder().name("Site B").code("USM-K2").build());
        UserSiteMembership existing = userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(target.getId()).siteId(site.getId()).isActive(true).build());

        mockMvc.perform(delete("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Reactivation: granting again after revoke must not violate the unique constraint (the
        // insertActiveIfAbsent+activate pairing must reactivate the same row, not attempt a
        // second insert).
        mockMvc.perform(put("/api/admin/users/{userId}/site-memberships/{siteId}", target.getId(), site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        UserSiteMembership reactivated = userSiteMembershipRepository.findByUserIdAndSiteId(target.getId(), site.getId())
                .orElseThrow();
        assertTrue(reactivated.getIsActive());
        assertTrue(reactivated.getId().equals(existing.getId()));
    }
}
