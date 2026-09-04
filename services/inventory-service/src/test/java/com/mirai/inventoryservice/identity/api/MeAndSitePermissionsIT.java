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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the required scenarios from docs/specs/authentication-and-authorization.md section 8:
 * active/inactive/absent/foreign-site membership, system-admin bypass, and the "empty list, not
 * implicit MAIN" rule for a user with no memberships.
 */
class MeAndSitePermissionsIT extends BaseIntegrationTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSiteMembershipRepository userSiteMembershipRepository;

    @Test
    void meReturnsTheResolvedUsersProfile() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin.persona@test.internal"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.systemAdmin").value(false));
    }

    @Test
    void meSitesIsEmptyForAUserWithNoMemberships() throws Exception {
        String token = employeeToken();

        mockMvc.perform(get("/api/v1/me/sites").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void meSitesListsOnlyActiveMemberships() throws Exception {
        String token = adminToken();
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        Site active = siteRepository.save(Site.builder().name("Active Site").code("ACTIVE-1").build());
        Site inactive = siteRepository.save(Site.builder().name("Inactive Site").code("INACTIVE-1").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(active.getId()).isActive(true).build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(inactive.getId()).isActive(false).build());

        mockMvc.perform(get("/api/v1/me/sites").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].siteCode").value("ACTIVE-1"));
    }

    @Test
    void permissionsReturns200ForActiveMembership() throws Exception {
        String token = adminToken();
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        Site site = siteRepository.save(Site.builder().name("Site A").code("SITE-A").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(site.getId()).isActive(true).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.systemAdmin").value(false))
                .andExpect(jsonPath("$.permissions", hasItem("costs:view")));
    }

    @Test
    void permissionsReturns403ForInactiveMembership() throws Exception {
        String token = adminToken();
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        Site site = siteRepository.save(Site.builder().name("Site B").code("SITE-B").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(site.getId()).isActive(false).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionsReturns403ForAbsentMembership() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Site C").code("SITE-C").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionsReturns403ForForeignSiteMembership() throws Exception {
        String token = adminToken();
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        Site home = siteRepository.save(Site.builder().name("Home Site").code("HOME").build());
        Site foreign = siteRepository.save(Site.builder().name("Foreign Site").code("FOREIGN").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(home.getId()).isActive(true).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", foreign.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionsReturns401ForAnUnauthenticatedRequest() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Site F").code("SITE-F").build());

        // No Authorization header at all - must be reported as "who are you" (401), not
        // "you can't have this" (403); the latter would leak that authorization, not
        // authentication, is what's missing.
        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permissionsReturns401ForAnInvalidToken() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Site G").code("SITE-G").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId())
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permissionsReturns404ForUnknownSite() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void systemAdminBypassesMembershipWithoutCreatingAMembershipRow() throws Exception {
        User systemAdmin = userRepository.save(User.builder()
                .email("system-admin.persona@test.internal")
                .fullName("System Admin Persona")
                .role(UserRole.EMPLOYEE)
                .isSystemAdmin(true)
                .build());
        String token = generateTestToken("system-admin-id", systemAdmin.getEmail(), "EMPLOYEE");
        Site site = siteRepository.save(Site.builder().name("Site D").code("SITE-D").build());

        long membershipsBefore = userSiteMembershipRepository.count();

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemAdmin").value(true));

        org.junit.jupiter.api.Assertions.assertEquals(
                membershipsBefore, userSiteMembershipRepository.count(),
                "system-admin bypass must not create a membership row");
    }

    @Test
    void employeePermissionsExcludeCostsView() throws Exception {
        String token = employeeToken();
        User employee = userRepository.findByEmail("employee.persona@test.internal").orElseThrow();
        Site site = siteRepository.save(Site.builder().name("Site E").code("SITE-E").build());
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(employee.getId()).siteId(site.getId()).isActive(true).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/permissions", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("costs:view"))));
    }
}
