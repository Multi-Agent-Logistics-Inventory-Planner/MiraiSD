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
 * Security/site-scoping tests for {@link SiteLootboxController} (.specs/phase-7-kuji-lootbox T-3,
 * AC-5). Mirrors {@code SiteShipmentControllerSecurityTest}'s role-gate and foreign-site rejection
 * assertions for the v1 route shape ({@code SiteAccessAuthorizationFilter} runs before
 * {@code @PreAuthorize}, so a caller with no membership at the path's {@code siteId} is rejected
 * with 403 before any role check).
 */
@DisplayName("SiteLootboxController (v1 site-scoped lootbox)")
class SiteLootboxControllerSecurityTest extends BaseIntegrationTest {

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
    @DisplayName("balance: 401 with no token")
    void getSiteBalance_noAuth_returnsUnauthorized() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/balance", siteId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("balance: 403 for a caller with no membership at the path's site")
    void getSiteBalance_foreignSite_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SL-BAL-FOREIGN").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/balance", foreignSite.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("balance: EMPLOYEE with membership at this site is allowed (not 401/403)")
    void getSiteBalance_employeeWithMembership_isNotRejectedByAuth() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SL-BAL-EMP");

        int statusCode = mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/balance", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }

    @Test
    @DisplayName("catalog: 403 for a caller with no membership at the path's site")
    void getSiteCatalog_foreignSite_returnsForbidden() throws Exception {
        String token = adminToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SL-CAT-FOREIGN").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/catalog", foreignSite.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("catalog: ADMIN with membership at this site is allowed (not 401/403)")
    void getSiteCatalog_adminWithMembership_isNotRejectedByAuth() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SL-CAT-ADMIN");

        int statusCode = mockMvc.perform(get("/api/v1/sites/{siteId}/lootbox/catalog", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }
}
