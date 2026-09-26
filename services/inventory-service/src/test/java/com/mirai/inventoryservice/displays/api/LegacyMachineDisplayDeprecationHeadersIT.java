package com.mirai.inventoryservice.displays.api;

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
 * .specs/phase-7-displays T-4, AC-5: legacy /api/machine-displays gains Deprecation and Link
 * headers; the new /api/v1/sites/{siteId}/machine-displays/** routes are unaffected. Mirrors
 * {@code shipments.api.LegacyShipmentDeprecationHeadersIT} exactly.
 */
@DisplayName("Legacy machine-display routes carry deprecation headers (v1 routes do not)")
class LegacyMachineDisplayDeprecationHeadersIT extends BaseIntegrationTest {

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
    @DisplayName("GET /api/machine-displays carries Deprecation and Link headers")
    void listActiveDisplays_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/machine-displays")
                        .header("Authorization", "Bearer " + employeeTokenWithMainMembership()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Sunset"));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/machine-displays/stale carries Deprecation and Link headers")
    void getStaleDisplays_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/machine-displays/stale")
                        .header("Authorization", "Bearer " + adminTokenWithMainMembership()));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/v1/sites/{siteId}/machine-displays does not carry legacy deprecation headers")
    void getV1SiteMachineDisplays_doesNotCarryDeprecationHeaders() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "DEPV1-DISP-1");

        mockMvc.perform(get("/api/v1/sites/{siteId}/machine-displays", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }
}
