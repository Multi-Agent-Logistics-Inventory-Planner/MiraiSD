package com.mirai.inventoryservice.kuji.api;

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
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security/site-scoping tests for {@link SiteKujiBoxController} (.specs/phase-7-kuji-lootbox T-3,
 * AC-5). Mirrors {@code SiteShipmentControllerSecurityTest}'s role-gate and foreign-site rejection
 * assertions for the v1 route shape ({@code SiteAccessAuthorizationFilter} runs before
 * {@code @PreAuthorize}, so a caller with no membership at the path's {@code siteId} is rejected
 * with 403 before any role check).
 */
@DisplayName("SiteKujiBoxController (v1 site-scoped kuji boxes)")
class SiteKujiBoxControllerSecurityTest extends BaseIntegrationTest {

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

    private String validOpenBoxRequestBody() {
        return """
                {
                    "productId": "%s",
                    "locationId": "%s",
                    "actorId": "%s",
                    "tiers": [{"label": "Tier A", "activeCount": 5}]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("open: 401 with no token")
    void openSiteBox_noAuth_returnsUnauthorized() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/sites/{siteId}/kuji-boxes", siteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOpenBoxRequestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("open: 403 for a caller with no membership at the path's site")
    void openSiteBox_foreignSite_returnsForbidden() throws Exception {
        String token = adminToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SK-OPEN-FOREIGN").build());

        mockMvc.perform(post("/api/v1/sites/{siteId}/kuji-boxes", foreignSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOpenBoxRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("open: 403 for EMPLOYEE role even with membership at this site (ADMIN/ASSISTANT_MANAGER only)")
    void openSiteBox_employeeRole_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SK-OPEN-EMP");

        mockMvc.perform(post("/api/v1/sites/{siteId}/kuji-boxes", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOpenBoxRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("open: ADMIN with membership at this site is allowed through auth (not 401/403)")
    void openSiteBox_adminWithMembership_isNotRejectedByAuth() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SK-OPEN-ADMIN");

        int statusCode = mockMvc.perform(post("/api/v1/sites/{siteId}/kuji-boxes", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOpenBoxRequestBody()))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }

    @Test
    @DisplayName("active-by-product: 401 with no token")
    void getSiteActiveBoxByProduct_noAuth_returnsUnauthorized() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/sites/{siteId}/kuji-boxes/by-product/{productId}/active",
                        siteId, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("active-by-product: 403 for a caller with no membership at the path's site")
    void getSiteActiveBoxByProduct_foreignSite_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("SK-READ-FOREIGN").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/kuji-boxes/by-product/{productId}/active",
                        foreignSite.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("active-by-product: EMPLOYEE with membership at this site is allowed (reads permit EMPLOYEE)")
    void getSiteActiveBoxByProduct_employeeWithMembership_isNotRejectedByAuth() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SK-READ-EMP");

        int statusCode = mockMvc.perform(get("/api/v1/sites/{siteId}/kuji-boxes/by-product/{productId}/active",
                        site.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }

    @Test
    @DisplayName("draw: EMPLOYEE role with membership is allowed through the role gate (EMPLOYEE+ required)")
    void recordSiteDraw_employeeWithMembership_isNotRejectedByAuth() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SK-DRAW-EMP");
        String body = """
                {"actorId": "%s", "draws": [{"tierId": "%s", "quantity": 1}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        int statusCode = mockMvc.perform(post("/api/v1/sites/{siteId}/kuji-boxes/{boxId}/draws",
                        site.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isNotIn(401, 403);
    }
}
