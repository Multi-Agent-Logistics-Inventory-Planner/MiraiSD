package com.mirai.inventoryservice.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the cost/MSRP exposure gap found while porting the permission model
 * to the backend (Workstream A): GET /api/products carried no @PreAuthorize, so unitCost/msrp
 * reached every authenticated role even though the frontend hid them for non-ADMIN roles.
 */
class ProductCostVisibilityIT extends BaseIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private String createProductAndGetId() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Cost Visibility Test Category")
                .slug("cost-visibility-test-category")
                .build());

        String productJson = """
                {
                    "sku": "COST-VIS-001",
                    "name": "Cost Visibility Test Product",
                    "categoryId": "%s",
                    "reorderPoint": 10,
                    "targetStockLevel": 50,
                    "leadTimeDays": 14,
                    "unitCost": 12.34,
                    "msrp": 29.99
                }
                """.formatted(category.getId());

        String responseBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(productJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asText();
    }

    @Test
    void adminSeesUnitCostAndMsrpOnDetail() throws Exception {
        String id = createProductAndGetId();

        mockMvc.perform(get("/api/products/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").value(12.34))
                .andExpect(jsonPath("$.msrp").value(29.99));
    }

    @Test
    void employeeDoesNotSeeUnitCostOrMsrpOnDetail() throws Exception {
        String id = createProductAndGetId();

        String body = mockMvc.perform(get("/api/products/" + id)
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.msrp").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // jsonPath().doesNotExist() also accepts an explicit null, but the published
        // contract declares these non-nullable-and-optional - absence is the contract-safe
        // representation, so assert the keys are genuinely missing from the wire format.
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("unitCost")).isFalse();
        assertThat(json.has("msrp")).isFalse();
    }

    @Test
    void assistantManagerSeesMsrpButNotUnitCost() throws Exception {
        String id = createProductAndGetId();

        mockMvc.perform(get("/api/products/" + id)
                        .header("Authorization", "Bearer " + assistantManagerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.msrp").value(29.99));
    }

    @Test
    void employeeDoesNotSeeUnitCostOrMsrpOnList() throws Exception {
        createProductAndGetId();

        String responseBody = mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode products = objectMapper.readTree(responseBody);
        JsonNode target = null;
        for (JsonNode product : products) {
            if ("COST-VIS-001".equals(product.path("sku").asText())) {
                target = product;
                break;
            }
        }

        assertThat(target).as("seeded product should be in the list response").isNotNull();
        // has(), not hasNonNull()/isNull(): redaction omits the field entirely (@JsonInclude
        // NON_NULL), and only has() distinguishes an absent key from an explicit JSON null -
        // hasNonNull() would accept both. See RedactedFieldsAreOmittedFromJsonIT.
        assertThat(target.has("unitCost")).isFalse();
        assertThat(target.has("msrp")).isFalse();
    }

    /**
     * Regression for the gap a follow-up review found: createProduct/updateProduct returned the
     * raw mapped DTO with no redaction at all, so an assistant manager (who lacks COSTS_VIEW)
     * received unitCost straight back in the mutation response even though every read endpoint
     * was already covered.
     */
    @Test
    void assistantManagerCreatingProductDoesNotSeeUnitCostInResponse() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Cost Visibility Create Category")
                .slug("cost-visibility-create-category")
                .build());

        String productJson = """
                {
                    "sku": "COST-VIS-CREATE-001",
                    "name": "Cost Visibility Create Product",
                    "categoryId": "%s",
                    "reorderPoint": 10,
                    "targetStockLevel": 50,
                    "leadTimeDays": 14,
                    "unitCost": 8.00,
                    "msrp": 19.99
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + assistantManagerToken())
                        .contentType("application/json")
                        .content(productJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.msrp").value(19.99));
    }

    @Test
    void assistantManagerUpdatingProductDoesNotSeeExistingUnitCostInResponse() throws Exception {
        String id = createProductAndGetId();
        Category category = categoryRepository.findAll().stream()
                .filter(c -> "Cost Visibility Test Category".equals(c.getName()))
                .findFirst().orElseThrow();

        String updateJson = """
                {
                    "sku": "COST-VIS-001",
                    "name": "Renamed By Assistant Manager",
                    "categoryId": "%s",
                    "reorderPoint": 10,
                    "targetStockLevel": 50,
                    "leadTimeDays": 14
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/products/" + id)
                        .header("Authorization", "Bearer " + assistantManagerToken())
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed By Assistant Manager"))
                .andExpect(jsonPath("$.unitCost").doesNotExist())
                .andExpect(jsonPath("$.msrp").value(29.99));
    }
}
