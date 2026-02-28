package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.inventory.RackInventory;
import com.mirai.inventoryservice.models.inventory.NotAssignedInventory;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.models.storage.Rack;
import com.mirai.inventoryservice.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for InventoryAggregateController.
 * Tests the optimized /api/inventory/by-product/{productId} endpoint.
 */
@DisplayName("InventoryAggregateController Integration Tests")
class InventoryAggregateControllerIT extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BoxBinRepository boxBinRepository;

    @Autowired
    private RackRepository rackRepository;

    @Autowired
    private BoxBinInventoryRepository boxBinInventoryRepository;

    @Autowired
    private RackInventoryRepository rackInventoryRepository;

    @Autowired
    private NotAssignedInventoryRepository notAssignedInventoryRepository;

    private Product testProduct;
    private BoxBin testBoxBin;
    private Rack testRack;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(Category.builder()
                .name("Test Category")
                .slug("test-category-" + UUID.randomUUID())
                .build());

        testProduct = productRepository.save(Product.builder()
                .sku("TEST-AGG-001")
                .name("Test Aggregate Product")
                .category(testCategory)
                .build());

        testBoxBin = boxBinRepository.save(BoxBin.builder().boxBinCode("B98").build());
        testRack = rackRepository.save(Rack.builder().rackCode("R98").build());
    }

    @Nested
    @DisplayName("GET /api/inventory/by-product/{productId}")
    class GetInventoryByProduct {

        @Test
        @DisplayName("Should return all inventory entries across location types")
        void getInventoryByProduct_multipleLocations_returnsAll() throws Exception {
            boxBinInventoryRepository.save(BoxBinInventory.builder()
                    .boxBin(testBoxBin)
                    .item(testProduct)
                    .quantity(5)
                    .build());

            rackInventoryRepository.save(RackInventory.builder()
                    .rack(testRack)
                    .item(testProduct)
                    .quantity(10)
                    .build());

            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId", is(testProduct.getId().toString())))
                    .andExpect(jsonPath("$.productSku", is("TEST-AGG-001")))
                    .andExpect(jsonPath("$.productName", is("Test Aggregate Product")))
                    .andExpect(jsonPath("$.totalQuantity", is(15)))
                    .andExpect(jsonPath("$.entries", hasSize(2)))
                    .andExpect(jsonPath("$.entries[*].locationType", containsInAnyOrder("BOX_BIN", "RACK")));
        }

        @Test
        @DisplayName("Should include NOT_ASSIGNED entries")
        void getInventoryByProduct_withNotAssigned_includesNotAssigned() throws Exception {
            notAssignedInventoryRepository.save(NotAssignedInventory.builder()
                    .item(testProduct)
                    .quantity(7)
                    .build());

            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalQuantity", is(7)))
                    .andExpect(jsonPath("$.entries", hasSize(1)))
                    .andExpect(jsonPath("$.entries[0].locationType", is("NOT_ASSIGNED")))
                    .andExpect(jsonPath("$.entries[0].locationLabel", is("Not Assigned")));
        }

        @Test
        @DisplayName("Should return empty entries for product with no inventory")
        void getInventoryByProduct_noInventory_returnsEmptyEntries() throws Exception {
            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId", is(testProduct.getId().toString())))
                    .andExpect(jsonPath("$.totalQuantity", is(0)))
                    .andExpect(jsonPath("$.entries", hasSize(0)));
        }

        @Test
        @DisplayName("Should return 404 for non-existent product")
        void getInventoryByProduct_nonExistentProduct_returns404() throws Exception {
            UUID randomId = UUID.randomUUID();
            mockMvc.perform(get("/api/inventory/by-product/{productId}", randomId)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for invalid UUID format")
        void getInventoryByProduct_invalidUuid_returns400() throws Exception {
            mockMvc.perform(get("/api/inventory/by-product/{productId}", "invalid-uuid")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should require authentication")
        void getInventoryByProduct_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should calculate correct total quantity from all locations")
        void getInventoryByProduct_multipleEntriesSameLocation_calculatesCorrectTotal() throws Exception {
            boxBinInventoryRepository.save(BoxBinInventory.builder()
                    .boxBin(testBoxBin)
                    .item(testProduct)
                    .quantity(3)
                    .build());

            notAssignedInventoryRepository.save(NotAssignedInventory.builder()
                    .item(testProduct)
                    .quantity(12)
                    .build());

            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalQuantity", is(15)));
        }
    }
}
