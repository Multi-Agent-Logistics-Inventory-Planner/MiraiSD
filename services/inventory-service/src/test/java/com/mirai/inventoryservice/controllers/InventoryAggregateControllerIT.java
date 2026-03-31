package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.BaseIntegrationTest;
import com.mirai.inventoryservice.models.Category;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.Site;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.storage.Location;
import com.mirai.inventoryservice.models.storage.StorageLocation;
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
 * Uses unified location_inventory table.
 */
@DisplayName("InventoryAggregateController Integration Tests")
class InventoryAggregateControllerIT extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationInventoryRepository locationInventoryRepository;

    private Product testProduct;
    private Site testSite;
    private StorageLocation boxBinsStorage;
    private StorageLocation racksStorage;
    private StorageLocation notAssignedStorage;
    private Location testBoxBinLocation;
    private Location testRackLocation;
    private Location testNotAssignedLocation;
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

        // Get or create test site
        testSite = siteRepository.findByCode("MAIN")
                .orElseGet(() -> siteRepository.save(Site.builder()
                        .code("MAIN")
                        .name("Main Warehouse")
                        .build()));

        // Get or create storage locations
        boxBinsStorage = storageLocationRepository.findByCodeAndSite_Code("BOX_BINS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(testSite)
                        .code("BOX_BINS")
                        .name("Box Bins")
                        .isDisplayOnly(false)
                        .hasDisplay(false)
                        .displayOrder(1)
                        .build()));

        racksStorage = storageLocationRepository.findByCodeAndSite_Code("RACKS", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(testSite)
                        .code("RACKS")
                        .name("Racks")
                        .isDisplayOnly(false)
                        .hasDisplay(false)
                        .displayOrder(2)
                        .build()));

        notAssignedStorage = storageLocationRepository.findByCodeAndSite_Code("NOT_ASSIGNED", "MAIN")
                .orElseGet(() -> storageLocationRepository.save(StorageLocation.builder()
                        .site(testSite)
                        .code("NOT_ASSIGNED")
                        .name("Not Assigned")
                        .isDisplayOnly(false)
                        .hasDisplay(false)
                        .displayOrder(99)
                        .build()));

        // Create test locations within storage locations
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
        testBoxBinLocation = locationRepository.save(Location.builder()
                .storageLocation(boxBinsStorage)
                .locationCode("B98" + uniqueSuffix)
                .build());

        testRackLocation = locationRepository.save(Location.builder()
                .storageLocation(racksStorage)
                .locationCode("R98" + uniqueSuffix)
                .build());

        testNotAssignedLocation = locationRepository
                .findByLocationCodeAndStorageLocation_Id("NA", notAssignedStorage.getId())
                .orElseGet(() -> locationRepository.save(Location.builder()
                        .storageLocation(notAssignedStorage)
                        .locationCode("NA")
                        .build()));
    }

    @Nested
    @DisplayName("GET /api/inventory/by-product/{productId}")
    class GetInventoryByProduct {

        @Test
        @DisplayName("Should return all inventory entries across location types")
        void getInventoryByProduct_multipleLocations_returnsAll() throws Exception {
            locationInventoryRepository.save(LocationInventory.builder()
                    .location(testBoxBinLocation)
                    .site(testSite)
                    .product(testProduct)
                    .quantity(5)
                    .build());

            locationInventoryRepository.save(LocationInventory.builder()
                    .location(testRackLocation)
                    .site(testSite)
                    .product(testProduct)
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
            locationInventoryRepository.save(LocationInventory.builder()
                    .location(testNotAssignedLocation)
                    .site(testSite)
                    .product(testProduct)
                    .quantity(7)
                    .build());

            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalQuantity", is(7)))
                    .andExpect(jsonPath("$.entries", hasSize(1)))
                    .andExpect(jsonPath("$.entries[0].locationType", is("NOT_ASSIGNED")))
                    .andExpect(jsonPath("$.entries[0].locationLabel", is("Not Assigned NA")));
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
            locationInventoryRepository.save(LocationInventory.builder()
                    .location(testBoxBinLocation)
                    .site(testSite)
                    .product(testProduct)
                    .quantity(3)
                    .build());

            locationInventoryRepository.save(LocationInventory.builder()
                    .location(testNotAssignedLocation)
                    .site(testSite)
                    .product(testProduct)
                    .quantity(12)
                    .build());

            mockMvc.perform(get("/api/inventory/by-product/{productId}", testProduct.getId())
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalQuantity", is(15)));
        }
    }
}
