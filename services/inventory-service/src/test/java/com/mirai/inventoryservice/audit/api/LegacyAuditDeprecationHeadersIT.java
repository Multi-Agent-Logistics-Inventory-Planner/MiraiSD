package com.mirai.inventoryservice.audit.api;

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

@DisplayName("Legacy audit-log route carries deprecation headers (v1 route does not)")
class LegacyAuditDeprecationHeadersIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;

    @Test
    @DisplayName("GET /api/audit-logs carries Deprecation and Link headers and no Sunset")
    void listLegacyAuditLogs_carriesDeprecationHeaders() throws Exception {
        var result = mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Sunset"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Deprecation")).matches("^@\\d+$");
        assertThat(result.getResponse().getHeader("Link"))
                .contains("docs/baseline/api-v1-map.md")
                .contains("rel=\"deprecation\"");
    }

    @Test
    @DisplayName("GET /api/v1/sites/{siteId}/audit-logs does not carry legacy deprecation headers")
    void listV1AuditLogs_doesNotCarryDeprecationHeaders() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("DEPV1-AUD").code("DEPV1-AUD-1").build());
        User user = userRepository.findByEmail("admin.persona@test.internal").orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/audit-logs", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }
}
