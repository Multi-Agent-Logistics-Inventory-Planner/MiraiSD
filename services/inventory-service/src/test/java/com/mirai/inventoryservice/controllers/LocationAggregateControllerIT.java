package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
import com.mirai.inventoryservice.repositories.CategoryRepository;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.LocationRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.SiteRepository;
import com.mirai.inventoryservice.repositories.StorageLocationRepository;
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
 * Uses the unified Location and LocationInventory tables.
 */
@DisplayName("LocationAggregateController Integration Tests")
class LocationAggregateControllerIT extends BaseIntegrationTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationInventoryRepository locationInventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Site testSite;
    private StorageLocation boxBinsStorage;
    private StorageLocation racksStorage;
    private Location testBoxBin;
    private Location testRack;
    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        // Get or create site
        testSite = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder()
                        .name("Main Warehouse")
                        .code("MAIN")
                        .build()));

        // Get or create storage locations
        boxBinsStorage = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(testSite)
                        .name("Box Bins")
                        .code("BOX_BINS")
                        .codePrefix("B")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(1)
                        .build()));

        racksStorage = storageLocationRepository.findByCodeAndSite_Code("RACKS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(testSite)
                        .name("Racks")
                        .code("RACKS")
                        .codePrefix("R")
                        .hasDisplay(false)
                        .isDisplayOnly(false)
                        .displayOrder(2)
                        .build()));

        // Create test category
        testCategory = categoryRepository.save(Category.builder()
                .name("Test Category")
                .slug("test-category-" + UUID.randomUUID())
                .build());

        // Create test locations
        testBoxBin = locationRepository.save(Location.builder()
                .storageLocation(boxBinsStorage)
                .locationCode("B99")
                .build());

        testRack = locationRepository.save(Location.builder()
                .storageLocation(racksStorage)
                .locationCode("R99")
                .build());

        // Create test product
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
                    .andExpect(jsonPath("$[*].locationType", hasItem("BOX_BINS")))
                    .andExpect(jsonPath("$[*].locationType", hasItem("RACKS")));
        }

        @Test
        @DisplayName("Should filter by location type when type parameter provided")
        void getLocationsWithCounts_filterByType_returnsOnlyMatchingType() throws Exception {
            mockMvc.perform(get("/api/locations/with-counts")
                            .param("type", "BOX_BIN")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].locationType", everyItem(equalTo("BOX_BINS"))));
        }

        @Test
        @DisplayName("Should return correct inventory counts")
        void getLocationsWithCounts_withInventory_returnsCorrectCounts() throws Exception {
            locationInventoryRepository.save(LocationInventory.builder()
                    .location(testBoxBin)
                    .site(testSite)
                    .product(testProduct)
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
