package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * spec.md phase-5d AC-1/AC-1b/AC-5-step-6 for the global {@code /api/v1/catalog/products} route:
 * {@link CatalogProductRequest} is master-identity-only, so every site-owned field is an
 * unrecognized property Jackson rejects with a 400 (not silently ignored), and a successful
 * creation inserts zero {@code site_products} rows anywhere.
 */
@DisplayName("CatalogProductController (v1 global catalog)")
class CatalogProductControllerIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/v1/catalog/products";

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SiteProductRepository siteProductRepository;

    private String validRequestJson(String sku) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sku", sku);
        node.put("name", "Catalog V1 Test Product");
        node.put("categoryId", seedCategory().getId().toString());
        return objectMapper.writeValueAsString(node);
    }

    private Category seedCategory() {
        return categoryRepository.save(Category.builder()
                .name("Catalog V1 Test Category " + System.nanoTime())
                .slug("catalog-v1-test-category-" + System.nanoTime())
                .build());
    }

    @ParameterizedTest(name = "POST rejects forbidden field: {0}")
    @ValueSource(strings = {
            "isActive", "unitCost", "msrp", "reorderPoint", "targetStockLevel",
            "leadTimeDays", "forecastingEnabled", "quantity", "initialStock"
    })
    @DisplayName("AC-1b: POST /api/v1/catalog/products rejects every site-owned field with 400")
    void createCatalogProduct_rejectsForbiddenField(String forbiddenField) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sku", "CATALOG-V1-FORBIDDEN-" + forbiddenField.toUpperCase());
        node.put("name", "Catalog V1 Test Product");
        node.put("categoryId", seedCategory().getId().toString());
        node.put(forbiddenField, forbiddenNumericOrBooleanValue(forbiddenField));

        long siteProductCountBefore = siteProductRepository.count();

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isBadRequest());

        assertThat(siteProductRepository.count()).isEqualTo(siteProductCountBefore);
    }

    private String forbiddenNumericOrBooleanValue(String field) {
        return switch (field) {
            case "isActive", "forecastingEnabled" -> "true";
            case "unitCost", "msrp" -> "9.99";
            default -> "5";
        };
    }

    @Test
    @DisplayName("AC-1b: a successful POST creates zero site_products rows anywhere")
    void createCatalogProduct_createsNoSiteProductRows() throws Exception {
        long siteProductCountBefore = siteProductRepository.count();

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(validRequestJson("CATALOG-V1-NO-ASSORTMENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("CATALOG-V1-NO-ASSORTMENT"))
                .andExpect(jsonPath("$.isActive").doesNotExist())
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.quantity").doesNotExist());

        assertThat(siteProductRepository.count()).isEqualTo(siteProductCountBefore);
    }

    @Test
    @DisplayName("POST with only master-identity fields succeeds for ADMIN")
    void createCatalogProduct_validRequest_succeeds() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(validRequestJson("CATALOG-V1-VALID")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Catalog V1 Test Product"));
    }

    @Test
    @DisplayName("A newly created (inactive) global product is findable by search")
    void searchCatalogProducts_findsNewlyCreatedInactiveProduct() throws Exception {
        String uniqueName = "Catalog V1 Searchable Product " + System.nanoTime();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sku", "CATALOG-V1-SEARCHABLE-" + System.nanoTime());
        node.put("name", uniqueName);
        node.put("categoryId", seedCategory().getId().toString());

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("search", uniqueName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(uniqueName));
    }

    @Test
    @DisplayName("Creating a child product returns the actual parentId, not the unpopulated shadow column")
    void createCatalogProduct_childProduct_returnsCorrectParentId() throws Exception {
        String parentJson = validRequestJson("CATALOG-V1-PARENT-" + System.nanoTime());
        String parentResponse = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(parentJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String parentId = objectMapper.readTree(parentResponse).get("id").asText();

        ObjectNode childNode = objectMapper.createObjectNode();
        childNode.put("sku", "CATALOG-V1-CHILD-" + System.nanoTime());
        childNode.put("name", "Catalog V1 Child Product");
        childNode.put("parentId", parentId);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(childNode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId));
    }
}
