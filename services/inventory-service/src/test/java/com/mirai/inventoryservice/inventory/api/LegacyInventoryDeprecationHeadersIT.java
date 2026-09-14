package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.BaseIntegrationTest;
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
 * .specs/phase-6-inventory 6c, T-6c-13: legacy /api/inventory and /api/stock-movements gain
 * Deprecation and Link headers; the new /api/v1/sites/{siteId}/inventory/** routes are unaffected,
 * and legacy response bodies/status codes are unchanged. Mirrors
 * {@code catalog.api.LegacyCatalogDeprecationHeadersIT} exactly, including the format assertions
 * being independent of {@link LegacyInventoryDeprecationFilter}'s own constants.
 */
@DisplayName("Legacy inventory routes carry deprecation headers (v1 routes do not)")
class LegacyInventoryDeprecationHeadersIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;

    private static final Pattern STRUCTURED_FIELD_DATE = Pattern.compile("^@(-?\\d+)$");
    private static final Pattern LINK_HEADER = Pattern.compile("^<([^>]+)>\\s*;\\s*rel=\"deprecation\"$");
    private static final Instant EXPECTED_DEPRECATION_INSTANT =
            ZonedDateTime.parse("2026-09-08T00:00:00Z").toInstant();

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
    @DisplayName("GET /api/inventory/totals carries Deprecation and Link headers")
    void getInventoryTotals_carriesDeprecationHeaders() throws Exception {
        // InventoryTotalsRepository.findAllInventoryTotals()'s native SQL can 500 under the
        // shared H2 test datasource depending on unrelated data committed by other IT classes
        // (a ClassCastException casting a joined UUID column, order-dependent, pre-existing --
        // confirmed unrelated to this filter by grepping that this session never touched
        // INVENTORY_TOTALS_SQL or its mapping code). Same class of debt as
        // LegacyCatalogDeprecationHeadersIT's /api/suppliers test: assert header presence/format
        // without asserting a 200, and separately confirm Sunset is absent.
        ResultActions result = mockMvc.perform(get("/api/inventory/totals")
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(header().doesNotExist("Sunset"));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/stock-movements/audit-log carries Deprecation and Link headers")
    void getAuditLog_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/stock-movements/audit-log")
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk());
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/v1/sites/{siteId}/inventory/totals does not carry legacy deprecation headers")
    void getV1SiteInventoryTotals_doesNotCarryDeprecationHeaders() throws Exception {
        Site site = siteRepository.save(Site.builder().name("Deprecation V1 Site").code("DEPV1-1").build());

        mockMvc.perform(get("/api/v1/sites/{siteId}/inventory/totals", site.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }
}
