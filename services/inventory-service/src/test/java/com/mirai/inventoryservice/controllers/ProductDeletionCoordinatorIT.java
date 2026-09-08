package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for {@code catalog.application.ProductDeletionCoordinator}
 * (docs: .specs/phase-5a-catalog-module-move/spec.md T-4), which replaced
 * {@code ProductService.deleteProduct}'s direct foreign-repository access with five
 * catalog-declared ports implemented in their owning modules
 * (shipments/analytics/displays/inventory/kuji). This had no direct test coverage before the
 * extraction — the only prior coverage was RBAC-only (ProductControllerSecurityIT). This class
 * proves the delete flow, including the parent/child cascade, still works end-to-end through the
 * real Spring context (all five port beans actually wired, not mocked).
 */
class ProductDeletionCoordinatorIT extends BaseIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private String createCategory() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Deletion Coordinator Test Category")
                .slug("deletion-coordinator-test-category-" + System.nanoTime())
                .build());
        return category.getId().toString();
    }

    private String createProduct(String sku, String categoryId, String parentId) throws Exception {
        String parentField = parentId == null ? "" : String.format(",\"parentId\":\"%s\"", parentId);
        String productJson = String.format("""
                {
                    "sku": "%s",
                    "name": "%s",
                    "categoryId": "%s",
                    "reorderPoint": 10,
                    "targetStockLevel": 50,
                    "leadTimeDays": 14
                    %s
                }
                """, sku, sku, categoryId, parentField);

        String responseBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content(productJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asText();
    }

    @Test
    void deletingAParentWithNoChildrenSucceeds() throws Exception {
        String categoryId = createCategory();
        String productId = createProduct("DEL-COORD-SOLO-" + System.nanoTime(), categoryId, null);

        mockMvc.perform(delete("/api/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAParentCascadesToItsChildren() throws Exception {
        String categoryId = createCategory();
        long nonce = System.nanoTime();
        String parentId = createProduct("DEL-COORD-PARENT-" + nonce, categoryId, null);
        String childId = createProduct("DEL-COORD-CHILD-" + nonce, categoryId, parentId);

        mockMvc.perform(delete("/api/products/" + parentId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        // The cascade (forecast/display/inventory/stock-movement cleanup via the new ports, then
        // productRepository.deleteAll(children)) must have removed the child too.
        mockMvc.perform(get("/api/products/" + parentId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/" + childId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }
}
