package com.mirai.inventoryservice.reviews.api;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Legacy review routes carry deprecation headers (v1 routes do not)")
class LegacyReviewDeprecationHeadersIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository membershipRepository;

    @Test
    @DisplayName("GET /api/reviews/users/tracked carries Deprecation and Link headers and no Sunset")
    void legacyTrackedUsers_carriesDeprecationHeaders() throws Exception {
        var result = mockMvc.perform(get("/api/reviews/users/tracked")
                        .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Sunset"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Deprecation")).matches("^@\\d+$");
        assertThat(result.getResponse().getHeader("Link")).contains("docs/baseline/api-v1-map.md");
    }

    @Test
    @DisplayName("GET /api/v1/sites/{siteId}/reviews/users/tracked does not carry legacy deprecation headers")
    void v1TrackedUsers_doesNotCarryDeprecationHeaders() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("DEPV1-REV").code("DEPV1-REV-1").build());
        User admin = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        membershipRepository.save(UserSiteMembership.builder()
                .userId(admin.getId()).siteId(site.getId()).isActive(true).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/reviews/users/tracked", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }

    @Test
    @DisplayName("legacy routes carry deprecation headers on authentication failures")
    void legacyRoutes_deprecationHeadersOnAuthFailures() throws Exception {
        mockMvc.perform(get("/api/reviews/users/tracked"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Deprecation"))
                .andExpect(header().exists("Link"));
    }
}
