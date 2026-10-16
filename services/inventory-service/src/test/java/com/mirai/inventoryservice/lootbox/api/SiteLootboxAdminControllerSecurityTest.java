package com.mirai.inventoryservice.lootbox.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security/site-scoping tests for {@link SiteLootboxAdminController} (.specs/phase-7-kuji-lootbox
 * T-3, AC-5). Mirrors {@code SiteShipmentControllerSecurityTest}'s role-gate and foreign-site
 * rejection assertions for the v1 route shape ({@code SiteAccessAuthorizationFilter} runs before
 * {@code @PreAuthorize}, so a caller with no membership at the path's {@code siteId} is rejected
 * with 403 before any role check).
 */
@DisplayName("SiteLootboxAdminController (v1 site-scoped lootbox admin)")
class SiteLootboxAdminControllerSecurityTest extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;

    private Site siteWithMembership(String email, String code) {
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    @Test
    @DisplayName("crates: 401 with no token")
    void listSiteCrates_noAuth_returnsUnauthorized() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/admin/crates", siteId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("crates: 403 for a caller with no membership at the path's site")
    void listSiteCrates_foreignSite_returnsForbidden() throws Exception {
        String token = adminToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SLA-CRATES-FOREIGN").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/admin/crates", foreignSite.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("crates: 403 for EMPLOYEE role even with membership at this site (ADMIN only)")
    void listSiteCrates_employeeRole_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SLA-CRATES-EMP");

        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/admin/crates", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("crates: ADMIN with membership at this site is allowed (not 401/403)")
    void listSiteCrates_adminWithMembership_isNotRejectedByAuth() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SLA-CRATES-ADMIN");

        int statusCode = mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/admin/crates", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }

    @Test
    @DisplayName("coin-stats: 403 for ASSISTANT_MANAGER role (ADMIN only)")
    void getSiteCoinStats_assistantManagerRole_returnsForbidden() throws Exception {
        String token = assistantManagerToken();
        Site site = siteWithMembership("assistant-manager.persona@test.internal", "SLA-STATS-AM");

        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/admin/coin-stats", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
