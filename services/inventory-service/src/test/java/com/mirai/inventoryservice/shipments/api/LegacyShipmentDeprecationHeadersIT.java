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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * .specs/phase-7-shipments T-5, AC-5: legacy /api/shipments and /api/tracking gain Deprecation
 * and Link headers; the new /api/v1/sites/{siteId}/shipments/** and .../tracking/** routes are
 * unaffected. Mirrors {@code inventory.api.LegacyInventoryDeprecationHeadersIT} exactly.
 */
@DisplayName("Legacy shipment/tracking routes carry deprecation headers (v1 routes do not)")
class LegacyShipmentDeprecationHeadersIT extends BaseIntegrationTest {

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

    private static final Pattern STRUCTURED_FIELD_DATE = Pattern.compile("^@(-?\\d+)$");
    private static final Pattern LINK_HEADER = Pattern.compile("^<([^>]+)>\\s*;\\s*rel=\"deprecation\"$");
    private static final Instant EXPECTED_DEPRECATION_INSTANT =
            ZonedDateTime.parse("2026-09-23T00:00:00Z").toInstant();

    private void assertDeprecationHeaders(ResultActions result) throws Exception {
        String deprecation = result.andReturn().getResponse().getHeader("Deprecation");
        Matcher dateMatcher = STRUCTURED_FIELD_DATE.matcher(deprecation);
        assertThat(dateMatcher.matches())
                .as("Deprecation header '%s' must be an RFC 8941 structured-fields Date (@<unix-timestamp>), not an HTTP-date",
                        deprecation)
                .isTrue();
        Instant actualInstant = Instant.ofEpochSecond(Long.parseLong(dateMatcher.group(1)));
        assertThat(actualInstant).isEqualTo(EXPECTED_DEPRECATION_INSTANT);

        String link = result.andReturn().getResponse().getHeader("Link");
        Matcher linkMatcher = LINK_HEADER.matcher(link);
        assertThat(linkMatcher.matches())
                .as("Link header '%s' must be RFC 8288 syntax with rel=\"deprecation\"", link)
                .isTrue();
        assertThat(linkMatcher.group(1)).contains("docs/baseline/api-v1-map.md");
    }

    @Test
    @DisplayName("GET /api/shipments carries Deprecation and Link headers")
    void listShipments_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Sunset"));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("POST /api/tracking/lookup carries Deprecation and Link headers")
    void lookupTracking_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/tracking/lookup")
                                .header("Authorization", "Bearer " + adminTokenWithMainMembership())
                                .contentType("application/json")
                                .content("{\"trackingNumber\": \"1Z999\"}"));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/v1/sites/{siteId}/shipments does not carry legacy deprecation headers")
    void getV1SiteShipments_doesNotCarryDeprecationHeaders() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "DEPV1-SHIP-1");

        mockMvc.perform(get("/api/v1/sites/{siteId}/shipments", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }

    @Test
    @DisplayName("GET /api/v1/sites/{siteId}/tracking/{trackingNumber} does not carry legacy deprecation headers")
    void getV1SiteTracking_doesNotCarryDeprecationHeaders() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "DEPV1-TRACK-1");

        ResultActions result = mockMvc.perform(
                get("/api/v1/sites/{siteId}/tracking/{trackingNumber}", site.getId(), "1Z999")
                        .header("Authorization", "Bearer " + token));
        assertThat(result.andReturn().getResponse().getStatus()).isNotIn(401, 403);
        result.andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }

    @Test
    @DisplayName("legacy routes carry deprecation headers on authentication failures")
    void legacyRoutes_deprecationHeadersOnAuthFailures() throws Exception {
        mockMvc.perform(get("/api/shipments"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Deprecation"))
                .andExpect(header().exists("Link"));
    }
}
