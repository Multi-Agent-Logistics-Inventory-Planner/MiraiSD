package com.mirai.inventoryservice.catalog.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.mirai.inventoryservice.BaseIntegrationTest;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * spec.md phase-5d AC-3: legacy /api/products, /api/categories and /api/suppliers gain
 * Deprecation and Link headers pointing at migration documentation; the new /api/v1/catalog/**
 * routes are unaffected, and legacy response bodies/status codes are unchanged.
 *
 * <p>The format assertions below are independent of {@link LegacyCatalogDeprecationFilter}'s own
 * constants: they parse the raw header value against RFC 9745/RFC 8941's structured-fields Date
 * syntax ({@code @<unix-timestamp>}) and RFC 8288's Link syntax, rather than comparing against
 * the implementation's literal strings, so a regression to the wrong syntax (e.g. an HTTP-date)
 * would fail here even if the implementation constant were "fixed" to match a bad test.
 */
@DisplayName("Legacy catalog routes carry deprecation headers (v1 routes do not)")
class LegacyCatalogDeprecationHeadersIT extends BaseIntegrationTest {

    // RFC 8941 sf-date: "@" followed by an optionally-signed integer (seconds since epoch).
    private static final Pattern STRUCTURED_FIELD_DATE = Pattern.compile("^@(-?\\d+)$");
    // RFC 8288 Link: "<" URI-reference ">" *( ";" parameter )
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
    @DisplayName("GET /api/products carries Deprecation and Link headers, body/status unchanged")
    void getProducts_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Sunset"));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/categories carries Deprecation and Link headers")
    void getCategories_carriesDeprecationHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/suppliers carries Deprecation and Link headers")
    void getSuppliers_carriesDeprecationHeaders() throws Exception {
        // GET /api/suppliers 500s under the H2 test datasource independent of this change: it
        // queries the mv_lead_time_stats materialized view, which V20/V30's raw SQL migrations
        // create in Postgres but which is never created here (this profile uses
        // ddl-auto=create-drop, not Flyway). Confirmed pre-existing by probing this route with
        // T-3's filter/config classes removed from the working tree. Out of scope for this
        // header-only task, so this asserts header presence/format without asserting a 200.
        ResultActions result = mockMvc.perform(get("/api/suppliers")
                        .header("Authorization", "Bearer " + userToken()));
        assertDeprecationHeaders(result);
    }

    @Test
    @DisplayName("GET /api/v1/catalog/products does not carry legacy deprecation headers")
    void getV1CatalogProducts_doesNotCarryDeprecationHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Link"));
    }
}
