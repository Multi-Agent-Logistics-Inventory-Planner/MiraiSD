package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserSiteMembership;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.identity.infrastructure.UserSiteMembershipRepository;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * spec.md phase-5d T-2: AC-2 (read semantics), AC-2b (permission matrix on the new authorization
 * surface), AC-2c (precise 404 semantics), and cost-visibility nulling for
 * {@code /api/v1/sites/{siteId}/products/**}.
 * <p>
 * Every test calls the relevant {@code *Token()} helper (which seeds the matching {@code User}
 * row) before {@link #siteWithMembership} looks that user up by email - membership can only be
 * granted to a user that already exists.
 */
@DisplayName("SiteProductController (v1 site-scoped catalog)")
class SiteProductControllerIT extends BaseIntegrationTest {

    @Autowired private SiteRepository siteRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSiteMembershipRepository userSiteMembershipRepository;

    private Site siteWithMembership(String email, String code) {
        Site site = siteRepository.save(Site.builder().name(code + " Site").code(code).build());
        grantMembership(email, site, true);
        return site;
    }

    private void grantMembership(String email, Site site, boolean active) {
        User user = userRepository.findByEmail(email).orElseThrow();
        userSiteMembershipRepository.save(UserSiteMembership.builder()
                .userId(user.getId()).siteId(site.getId()).isActive(active).build());
    }

    private Product newProduct(String label) {
        Category category = categoryRepository.save(Category.builder()
                .name("SiteProduct IT Category " + label + " " + System.nanoTime())
                .slug("site-product-it-category-" + label.toLowerCase() + "-" + System.nanoTime())
                .build());
        return productRepository.save(Product.builder()
                .sku("SITE-PRODUCT-IT-" + label + "-" + System.nanoTime())
                .name("SiteProduct IT Product " + label)
                .category(category)
                .unitCost(new BigDecimal("1.00"))
                .msrp(new BigDecimal("2.00"))
                .reorderPoint(10)
                .targetStockLevel(50)
                .leadTimeDays(14)
                .build());
    }

    // ==================== AC-2: read semantics ====================

    @Test
    @DisplayName("AC-2: GET detail for a product never carried at this site is 200, not 404")
    void getSiteProduct_absentRow_returnsNotStockedWithGlobalFallback() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-ABSENT");
        Product product = newProduct("Absent");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products/{productId}", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isStocked").value(false))
                .andExpect(jsonPath("$.unitCost").value(1.00))
                .andExpect(jsonPath("$.reorderPoint").value(10))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("AC-2c: a globally nonexistent product is 404")
    void getSiteProduct_globallyNonexistentProduct_returns404() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-NOPROD");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products/{productId}", site.getId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-2: assortment PUT upserts - creates the row and carries the product")
    void updateAssortment_upsertsRow() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-UPSERT");
        Product product = newProduct("Upsert");

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isStocked").value(true))
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    @DisplayName("AC-2c: settings PUT against a product this site has never carried is 404, not a partial upsert")
    void updateSettings_noRowExists_returns404() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-NOROW");
        Product product = newProduct("NoRow");

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0, \"msrp\": 9.99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-2/AC-6: settings PUT on a carried product applies the override and advances the version")
    void updateSettings_onCarriedProduct_appliesOverride() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-SETTINGS");
        Product product = newProduct("Settings");

        String assortmentResponse = mockMvc.perform(
                        put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"isStocked\": true}"))
                .andReturn().getResponse().getContentAsString();
        long version = objectMapper.readTree(assortmentResponse).get("version").asLong();

        ObjectNode settings = objectMapper.createObjectNode();
        settings.put("expectedVersion", version);
        settings.put("msrp", "15.50");

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msrp").value(15.50))
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    @DisplayName("AC-6: an omitted override field is left unchanged; an explicit null clears it back to global inheritance")
    void updateSettings_omittedLeavesUnchanged_explicitNullClearsOverride() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-TRISTATE");
        Product product = newProduct("TriState");

        String assortmentResponse = mockMvc.perform(
                        put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"isStocked\": true}"))
                .andReturn().getResponse().getContentAsString();
        long version = objectMapper.readTree(assortmentResponse).get("version").asLong();

        // Set an override on msrp, omitting reorderPoint entirely.
        ObjectNode setOverride = objectMapper.createObjectNode();
        setOverride.put("expectedVersion", version);
        setOverride.put("msrp", "15.50");
        String afterSet = mockMvc.perform(
                        put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(setOverride)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reorderPoint").value(10)) // unchanged, inherited from global
                .andReturn().getResponse().getContentAsString();
        long versionAfterSet = objectMapper.readTree(afterSet).get("version").asLong();

        // Explicit null on msrp clears the override; reorderPoint stays omitted (still untouched).
        ObjectNode clearOverride = objectMapper.createObjectNode();
        clearOverride.put("expectedVersion", versionAfterSet);
        clearOverride.putNull("msrp");
        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(clearOverride)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msrp").value(2.00)) // back to the global product's msrp
                .andExpect(jsonPath("$.reorderPoint").value(10));
    }

    @Test
    @DisplayName("AC-6: settings PUT with a stale version is a 409, not last-write-wins")
    void updateSettings_staleVersion_returns409() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-STALE");
        Product product = newProduct("Stale");

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 999, \"msrp\": 15.50}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("T-2 review: a malformed (non-numeric) expectedVersion is rejected, not coerced to 0")
    void updateSettings_malformedVersion_returns400() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-MALFORMED-VERSION");
        Product product = newProduct("MalformedVersion");

        // A fresh assortment row's version is 0, so a naive "coerce unparsable to 0" bug would
        // let this malformed request slip through as if it correctly targeted version 0.
        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": \"nonsense\", \"msrp\": 15.50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T-2 review follow-up: a fractional expectedVersion is rejected, not truncated to 0")
    void updateSettings_fractionalVersion_returns400() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-FRAC-VER");
        Product product = newProduct("FractionalVersion");

        // Same version-0 trap as the non-numeric-string case: Jackson's default Long coercion
        // truncates a JSON float like 0.9 to 0 instead of rejecting it.
        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0.9, \"msrp\": 15.50}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC-2b: permission matrix ====================

    @Test
    @DisplayName("AC-2b: EMPLOYEE can read but cannot mutate assortment or settings")
    void employee_canReadButNotMutate() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SP-EMP");
        Product product = newProduct("Employee");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0, \"msrp\": 9.99}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-2b: ASSISTANT_MANAGER can mutate assortment and settings")
    void assistantManager_canMutate() throws Exception {
        String token = assistantManagerToken();
        Site site = siteWithMembership("assistant-manager.persona@test.internal", "SP-AM");
        Product product = newProduct("AssistantManager");

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0, \"msrp\": 9.99}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-2b: ADMIN can mutate assortment and settings")
    void admin_canMutate() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-ADMIN");
        Product product = newProduct("Admin");

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0, \"msrp\": 9.99}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-2b: a caller with no membership is rejected at the filter on both reads and mutations")
    void noMembership_rejectedOnReadsAndMutations() throws Exception {
        String employeeTok = employeeToken();
        String adminTok = adminToken();
        Site site = siteRepository.save(Site.builder().name("No Membership Site").code("SP-NOMEM").build());
        Product product = newProduct("NoMembership");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products", site.getId())
                        .header("Authorization", "Bearer " + employeeTok))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + adminTok)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + adminTok)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0, \"msrp\": 9.99}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-2b: a caller whose membership was revoked is rejected on both reads and mutations")
    void revokedMembership_rejectedOnReadsAndMutations() throws Exception {
        String token = adminToken();
        Site site = siteRepository.save(Site.builder().name("Revoked Site").code("SP-REVOKED").build());
        grantMembership("admin.persona@test.internal", site, false);
        Product product = newProduct("Revoked");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products", site.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/assortment", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"isStocked\": true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sites/{siteId}/products/{productId}/settings", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedVersion\": 0, \"msrp\": 9.99}"))
                .andExpect(status().isForbidden());
    }

    // ==================== Cost visibility ====================

    @Test
    @DisplayName("Cost visibility: EMPLOYEE sees neither unitCost nor msrp")
    void employee_costAndMsrpNulled() throws Exception {
        String token = employeeToken();
        Site site = siteWithMembership("employee.persona@test.internal", "SP-COST-EMP");
        Product product = newProduct("CostEmployee");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products/{productId}", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.msrp").doesNotExist());
    }

    @Test
    @DisplayName("Cost visibility: ASSISTANT_MANAGER sees msrp but not unitCost")
    void assistantManager_seesMsrpNotUnitCost() throws Exception {
        String token = assistantManagerToken();
        Site site = siteWithMembership("assistant-manager.persona@test.internal", "SP-COST-AM");
        Product product = newProduct("CostAssistantManager");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products/{productId}", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.msrp").value(2.00));
    }

    @Test
    @DisplayName("Cost visibility: ADMIN sees both unitCost and msrp")
    void admin_seesUnitCostAndMsrp() throws Exception {
        String token = adminToken();
        Site site = siteWithMembership("admin.persona@test.internal", "SP-COST-ADMIN");
        Product product = newProduct("CostAdmin");

        mockMvc.perform(get("/api/v1/sites/{siteId}/products/{productId}", site.getId(), product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").value(1.00))
                .andExpect(jsonPath("$.msrp").value(2.00));
    }
}
