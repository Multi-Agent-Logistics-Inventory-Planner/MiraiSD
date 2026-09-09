package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.auth.RateLimitingFilter;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * spec.md phase-5d T-4 / AC-5: the phase's exit-gate proof that one global product can be carried
 * at two sites with materially different, independently-mutable settings - not merely that an
 * unwritten site's row is unaffected by another site's writes.
 */
@DisplayName("Catalog v1 exit gate (AC-5): one product, independently configured at two sites")
class CatalogMultiSiteExitGateIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private RateLimitingFilter rateLimitingFilter;

    private Site siteWithMembership(String email, String code) {
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(true).build());
        return site;
    }

    private String createGlobalProduct(String token, String sku) throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("AC-5 Exit Gate Category " + System.nanoTime())
                .slug("ac5-exit-gate-category-" + System.nanoTime())
                .build());

        ObjectNode node = objectMapper.createObjectNode();
        node.put("sku", sku);
        node.put("name", "AC-5 Exit Gate Product " + sku);
        node.put("categoryId", category.getId().toString());

        String response = mockMvc.perform(post("/api/v1/catalog/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private long upsertAssortment(String token, String siteId, String productId) throws Exception {
        String response = mockMvc.perform(
                        put("/api/v1/sites/{siteId}/products/{productId}/assortment", siteId, productId)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"isStocked\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isStocked").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("version").asLong();
    }

    private JsonNode setSettings(String token, String siteId, String productId, long expectedVersion,
                                  BigDecimal msrp, int reorderPoint) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expectedVersion", expectedVersion);
        body.put("msrp", msrp);
        body.put("reorderPoint", reorderPoint);

        String response = mockMvc.perform(
                        put("/api/v1/sites/{siteId}/products/{productId}/settings", siteId, productId)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode getSiteProduct(String token, String siteId, String productId) throws Exception {
        String response = mockMvc.perform(
                        get("/api/v1/sites/{siteId}/products/{productId}", siteId, productId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    @DisplayName("AC-5: one global product carried and independently configured at MAIN and SECOND")
    void oneGlobalProduct_independentlyConfiguredAtBothSites() throws Exception {
        String token = adminToken();
        Site main = siteWithMembership("admin.persona@test.internal", "AC5-MAIN");
        Site second = siteWithMembership("admin.persona@test.internal", "AC5-SECOND");

        // Step 1: create one global product, zero assortment rows.
        String productId = createGlobalProduct(token, "AC5-EXIT-GATE-" + System.nanoTime());

        // Step 2: assortment-upsert it at both sites - proving a product can be carried at both.
        long mainVersion = upsertAssortment(token, main.getId().toString(), productId);
        long secondVersion = upsertAssortment(token, second.getId().toString(), productId);

        rateLimitingFilter.clearBuckets();

        // Step 3: distinct settings at each site.
        BigDecimal mainMsrp = new BigDecimal("19.99");
        BigDecimal secondMsrp = new BigDecimal("24.99");
        int mainReorderPoint = 5;
        int secondReorderPoint = 15;

        JsonNode mainAfterInitialSet = setSettings(token, main.getId().toString(), productId, mainVersion, mainMsrp, mainReorderPoint);
        mainVersion = mainAfterInitialSet.get("version").asLong();
        assertThat(mainAfterInitialSet.get("msrp").decimalValue()).isEqualByComparingTo(mainMsrp);
        assertThat(mainAfterInitialSet.get("reorderPoint").asInt()).isEqualTo(mainReorderPoint);

        JsonNode secondAfterInitialSet = setSettings(token, second.getId().toString(), productId, secondVersion, secondMsrp, secondReorderPoint);
        secondVersion = secondAfterInitialSet.get("version").asLong();
        assertThat(secondAfterInitialSet.get("msrp").decimalValue()).isEqualByComparingTo(secondMsrp);
        assertThat(secondAfterInitialSet.get("reorderPoint").asInt()).isEqualTo(secondReorderPoint);

        rateLimitingFilter.clearBuckets();

        // Step 4: update MAIN again; assert SECOND's *entire* persisted row is byte-for-byte
        // unchanged (not just the two fields this test happens to touch), and vice versa.
        BigDecimal mainMsrpUpdated = new BigDecimal("29.99");
        int mainReorderPointUpdated = 8;
        JsonNode mainAfterUpdate = setSettings(token, main.getId().toString(), productId, mainVersion, mainMsrpUpdated, mainReorderPointUpdated);
        mainVersion = mainAfterUpdate.get("version").asLong();

        JsonNode secondUnaffected = getSiteProduct(token, second.getId().toString(), productId);
        assertThat(secondUnaffected).isEqualTo(secondAfterInitialSet);

        BigDecimal secondMsrpUpdated = new BigDecimal("34.99");
        int secondReorderPointUpdated = 20;
        JsonNode secondAfterUpdate = setSettings(token, second.getId().toString(), productId, secondVersion, secondMsrpUpdated, secondReorderPointUpdated);
        secondVersion = secondAfterUpdate.get("version").asLong();

        JsonNode mainUnaffected = getSiteProduct(token, main.getId().toString(), productId);
        assertThat(mainUnaffected).isEqualTo(mainAfterUpdate);

        rateLimitingFilter.clearBuckets();

        // Step 5: deactivate the product's assortment at SECOND only.
        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", second.getId(), productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isStocked").value(false));

        JsonNode mainStillStocked = getSiteProduct(token, main.getId().toString(), productId);
        assertThat(mainStillStocked.get("isStocked").asBoolean()).isTrue();
        assertThat(mainStillStocked.get("msrp").decimalValue()).isEqualByComparingTo(mainMsrpUpdated);
        assertThat(mainStillStocked.get("reorderPoint").asInt()).isEqualTo(mainReorderPointUpdated);

        // SECOND's retained (de-assorted) row keeps its saved overrides per AC-2's retained-row rule.
        JsonNode secondDeassorted = getSiteProduct(token, second.getId().toString(), productId);
        assertThat(secondDeassorted.get("isStocked").asBoolean()).isFalse();
        assertThat(secondDeassorted.get("msrp").decimalValue()).isEqualByComparingTo(secondMsrpUpdated);
        assertThat(secondDeassorted.get("reorderPoint").asInt()).isEqualTo(secondReorderPointUpdated);

        rateLimitingFilter.clearBuckets();

        // Step 6: the global read returns only master-identity fields, no per-site values from
        // either site. Checked with JsonNode.has() (key absence), not doesNotExist(), since
        // doesNotExist() only fails on a present-with-non-null value and would pass even if the
        // response carried e.g. "targetStockLevel": null.
        String globalResponse = mockMvc.perform(get("/api/v1/catalog/products/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode globalNode = objectMapper.readTree(globalResponse);
        assertThat(globalNode.get("id").asText()).isEqualTo(productId);

        List<String> siteOwnedFields = List.of(
                "isActive", "unitCost", "msrp", "reorderPoint", "targetStockLevel",
                "leadTimeDays", "forecastingEnabled", "quantity", "initialStock", "isStocked");
        for (String field : siteOwnedFields) {
            assertThat(globalNode.has(field))
                    .as("global product response must not carry site-owned field '%s'", field)
                    .isFalse();
        }
    }
}
