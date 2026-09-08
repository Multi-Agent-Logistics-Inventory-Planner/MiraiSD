package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the ShipmentResponseDTO.totalCost / ShipmentItemResponseDTO.unitCost
 * exposure gap found alongside the ProductController one while porting the permission model to
 * the backend (Workstream A).
 */
class ShipmentCostVisibilityIT extends BaseIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private String createShipmentAndGetId() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Shipment Cost Visibility Category")
                .slug("shipment-cost-visibility-category")
                .build());

        Product product = productRepository.save(Product.builder()
                .sku("SHIP-COST-VIS-001")
                .name("Shipment Cost Visibility Product")
                .category(category)
                .reorderPoint(5)
                .targetStockLevel(20)
                .leadTimeDays(7)
                .unitCost(BigDecimal.valueOf(3))
                .isActive(true)
                .quantity(0)
                .build());

        String shipmentJson = """
                {
                    "status": "PENDING",
                    "orderDate": "2026-01-01",
                    "totalCost": 42.50,
                    "items": [
                        { "itemId": "%s", "orderedQuantity": 10, "unitCost": 4.25 }
                    ]
                }
                """.formatted(product.getId());

        String responseBody = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(shipmentJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asText();
    }

    @Test
    void adminSeesTotalCostAndItemUnitCost() throws Exception {
        String id = createShipmentAndGetId();

        mockMvc.perform(get("/api/shipments/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(42.50))
                .andExpect(jsonPath("$.items[0].unitCost").value(4.25));
    }

    @Test
    void employeeDoesNotSeeTotalCostOrItemUnitCost() throws Exception {
        String id = createShipmentAndGetId();

        String body = mockMvc.perform(get("/api/shipments/" + id)
                        .header("Authorization", "Bearer " + employeeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").doesNotExist())
                .andExpect(jsonPath("$.items[0].unitCost").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Absence, not null - see RedactedFieldsAreOmittedFromJsonIT for why.
        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(json.has("totalCost")).isFalse();
        org.assertj.core.api.Assertions.assertThat(
                json.path("items").get(0).has("unitCost")).isFalse();
    }

    @Test
    void assistantManagerDoesNotSeeCostsEither() throws Exception {
        String id = createShipmentAndGetId();

        mockMvc.perform(get("/api/shipments/" + id)
                        .header("Authorization", "Bearer " + assistantManagerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").doesNotExist());
    }
}
