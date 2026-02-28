package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.models.storage.Rack;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import com.mirai.inventoryservice.repositories.BoxBinRepository;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.RackRepository;
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
 * Integration tests for LocationAggregateController.
 * Tests the optimized /api/locations/with-counts endpoint.
 */
@DisplayName("LocationAggregateController Integration Tests")
class LocationAggregateControllerIT extends BaseIntegrationTest {

    @Autowired
    private BoxBinRepository boxBinRepository;

    @Autowired
    private RackRepository rackRepository;

    @Autowired
    private BoxBinInventoryRepository boxBinInventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private BoxBin testBoxBin;
    private Rack testRack;
    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(Category.builder()
                .name("Test Category")
                .slug("test-category-" + UUID.randomUUID())
                .build());

        testBoxBin = boxBinRepository.save(BoxBin.builder().boxBinCode("B99").build());
        testRack = rackRepository.save(Rack.builder().rackCode("R99").build());
        testProduct = productRepository.save(Product.builder()
                .sku("TEST-001")
                .name("Test Product")
                .category(testCategory)
                .build());
    }

    @Nested
    @DisplayName("GET /api/locations/with-counts")
    class GetLocationsWithCounts {

        @Test
        @DisplayName("Should return all locations with inventory counts")
        void getAllLocationsWithCounts_returnsAllTypes() throws Exception {
            mockMvc.perform(get("/api/locations/with-counts")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)))
                    .andExpect(jsonPath("$[*].locationType", hasItem("BOX_BIN")))
                    .andExpect(jsonPath("$[*].locationType", hasItem("RACK")));
        }

        @Test
        @DisplayName("Should filter by location type when type parameter provided")
        void getLocationsWithCounts_filterByType_returnsOnlyMatchingType() throws Exception {
            mockMvc.perform(get("/api/locations/with-counts")
                            .param("type", "BOX_BIN")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].locationType", everyItem(equalTo("BOX_BIN"))));
        }

        @Test
        @DisplayName("Should return correct inventory counts")
        void getLocationsWithCounts_withInventory_returnsCorrectCounts() throws Exception {
            boxBinInventoryRepository.save(BoxBinInventory.builder()
                    .boxBin(testBoxBin)
                    .item(testProduct)
                    .quantity(10)
                    .build());

            mockMvc.perform(get("/api/locations/with-counts")
                            .param("type", "BOX_BIN")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.locationCode=='B99')].inventoryRecords", hasItem(1)))
                    .andExpect(jsonPath("$[?(@.locationCode=='B99')].totalQuantity", hasItem(10)));
        }

        @Test
        @DisplayName("Should return zero counts for empty locations")
        void getLocationsWithCounts_emptyLocation_returnsZeroCounts() throws Exception {
            mockMvc.perform(get("/api/locations/with-counts")
                            .param("type", "RACK")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.locationCode=='R99')].inventoryRecords", hasItem(0)))
                    .andExpect(jsonPath("$[?(@.locationCode=='R99')].totalQuantity", hasItem(0)));
        }

        @Test
        @DisplayName("Should return empty list for NOT_ASSIGNED type")
        void getLocationsWithCounts_notAssignedType_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/api/locations/with-counts")
                            .param("type", "NOT_ASSIGNED")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should require authentication")
        void getLocationsWithCounts_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/locations/with-counts"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
