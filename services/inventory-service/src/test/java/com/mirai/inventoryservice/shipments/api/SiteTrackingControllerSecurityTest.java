package com.mirai.inventoryservice.shipments.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security/site-scoping tests for {@link SiteTrackingController} (.specs/phase-7-shipments T-5,
 * AC-5), mirroring {@link SiteShipmentControllerSecurityTest}'s pattern.
 */
@DisplayName("SiteTrackingController (v1 site-scoped tracking)")
class SiteTrackingControllerSecurityTest extends BaseIntegrationTest {

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
    @DisplayName("lookup: 401 with no token")
    void lookupSiteTracking_noAuth_returnsUnauthorized() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Anon Site").code("ST-ANON").build());

        mockMvc.perform(post("/api/v1/sites/{siteId}/tracking/lookup", site.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\": \"1Z999\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lookup: 403 for a caller with no membership at the path's site")
    void lookupSiteTracking_foreignSite_returnsForbidden() throws Exception {
        String token = adminToken();
        Site foreignSite = siteRepository.save(Site.builder().name("Foreign Site").code("ST-FOREIGN").build());

        mockMvc.perform(post("/api/v1/sites/{siteId}/tracking/lookup", foreignSite.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\": \"1Z999\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lookup: 403 for EMPLOYEE role even with membership at this site (ADMIN/ASSISTANT_MANAGER only)")
    void lookupSiteTracking_employeeRole_returnsForbidden() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "ST-LOOKUP-EMP");

        mockMvc.perform(post("/api/v1/sites/{siteId}/tracking/lookup", site.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\": \"1Z999\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getTracking: ADMIN with membership at this site is allowed through auth (not 401/403)")
    void getSiteTracking_adminWithMembership_isNotRejectedByAuth() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "ST-GET-ADMIN");

        int statusCode = mockMvc.perform(get("/api/v1/sites/{siteId}/tracking/{trackingNumber}",
                        site.getId(), "1Z999")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(statusCode).isNotIn(401, 403);
    }
}
