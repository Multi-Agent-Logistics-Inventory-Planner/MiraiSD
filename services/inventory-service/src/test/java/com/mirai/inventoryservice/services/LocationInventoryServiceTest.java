package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.inventory.domain.InventoryNotFoundException;
import com.mirai.inventoryservice.inventory.domain.InvalidInventoryOperationException;
import com.mirai.inventoryservice.sites.domain.LocationNotFoundException;
import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.application.LocationService;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import com.mirai.inventoryservice.catalog.application.CatalogEntityAccess;
import com.mirai.inventoryservice.inventory.application.LocationInventoryService;
import com.mirai.inventoryservice.inventory.application.StockMovementService;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LocationInventoryService.
 * Tests the unified inventory service that handles all location types.
 */
@ExtendWith(MockitoExtension.class)
class LocationInventoryServiceTest {

    @Mock
    private LocationInventoryRepository locationInventoryRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StorageLocationRepository storageLocationRepository;

    @Mock
    private LocationService locationService;

    @Mock
    private CatalogEntityAccess catalogEntityAccess;

    @Mock
    private StockMovementService stockMovementService;

    @InjectMocks
    private LocationInventoryService locationInventoryService;

    private Site testSite;
    private StorageLocation testStorageLocation;
    private Location testLocation;
    private Product testProduct;
    private LocationInventory testInventory;
    private Category testCategory;
    private UUID siteId;
    private UUID storageLocationId;
    private UUID locationId;
    private UUID productId;
    private UUID inventoryId;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        storageLocationId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        productId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();

        testSite = Site.builder()
                .id(siteId)
                .code("MAIN")
                .name("Main Store")
                .build();

        testStorageLocation = StorageLocation.builder()
                .id(storageLocationId)
                .site(testSite)
                .code("BOX_BINS")
                .name("Box Bins")
                .isDisplayOnly(false)
                .hasDisplay(false)
                .build();

        testLocation = Location.builder()
                .id(locationId)
                .storageLocation(testStorageLocation)
                .locationCode("B1")
                .build();

        testCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Test Category")
                .slug("test-category")
                .build();

        testProduct = Product.builder()
                .id(productId)
                .sku("SKU-001")
                .name("Test Product")
                .category(testCategory)
                .build();

        testInventory = LocationInventory.builder()
                .id(inventoryId)
                .location(testLocation)
                .site(testSite)
                .product(testProduct)
                .quantity(10)
                .build();
    }

    @Nested
    @DisplayName("getInventoryById")
    class GetInventoryByIdTests {

        @Test
        @DisplayName("should return inventory when found")
        void shouldReturnInventoryWhenFound() {
            when(locationInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));

            LocationInventory result = locationInventoryService.getInventoryById(inventoryId);

            assertEquals(testInventory, result);
        }

        @Test
        @DisplayName("should throw InventoryNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(locationInventoryRepository.findById(inventoryId)).thenReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class, () ->
                    locationInventoryService.getInventoryById(inventoryId));
        }
    }

    @Nested
    @DisplayName("listInventoryAtLocation")
    class ListInventoryAtLocationTests {

        @Test
        @DisplayName("should return all inventory at location")
        void shouldReturnAllInventoryAtLocation() {
            LocationInventory inventory2 = LocationInventory.builder()
                    .id(UUID.randomUUID())
                    .location(testLocation)
                    .site(testSite)
                    .product(Product.builder().id(UUID.randomUUID()).sku("SKU-002").name("Product 2").category(testCategory).build())
                    .quantity(5)
                    .build();

            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationInventoryRepository.findByLocation_Id(locationId))
                    .thenReturn(List.of(testInventory, inventory2));

            List<LocationInventory> result = locationInventoryService.listInventoryAtLocation(locationId);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no inventory exists")
        void shouldReturnEmptyListWhenNoInventory() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(locationInventoryRepository.findByLocation_Id(locationId)).thenReturn(List.of());

            List<LocationInventory> result = locationInventoryService.listInventoryAtLocation(locationId);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should throw LocationNotFoundException when location not found")
        void shouldThrowExceptionWhenLocationNotFound() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            assertThrows(LocationNotFoundException.class, () ->
                    locationInventoryService.listInventoryAtLocation(locationId));
        }
    }

    @Nested
    @DisplayName("addInventory")
    class AddInventoryTests {

        @Test
        @DisplayName("should look up the product via CatalogEntityAccess.requireManagedProduct and create tracked inventory")
        void shouldCreateInventoryViaCatalogFacades() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(catalogEntityAccess.requireManagedProduct(productId)).thenReturn(testProduct);
            when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                    .thenReturn(Optional.empty());
            when(stockMovementService.createInventoryWithTracking(
                    any(), eq(locationId), eq(testProduct), eq(10),
                    any(), any(), any(), any(), any()))
                    .thenReturn(inventoryId);
            when(locationInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));

            LocationInventory result = locationInventoryService.addInventory(
                    locationId, productId, 10, UUID.randomUUID(), null);

            assertEquals(testInventory, result);
            verify(catalogEntityAccess, times(1)).requireManagedProduct(productId);
            verify(catalogEntityAccess, never()).getReference(any());
            verify(stockMovementService, times(1)).rejectIfCustomKujiParent(testProduct);
        }

        @Test
        @DisplayName("should propagate ProductNotFoundException from CatalogEntityAccess.requireManagedProduct when product does not exist")
        void shouldThrowWhenProductNotFound() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(catalogEntityAccess.requireManagedProduct(productId))
                    .thenThrow(new com.mirai.inventoryservice.catalog.domain.ProductNotFoundException(
                            "Product not found: " + productId));

            assertThrows(com.mirai.inventoryservice.catalog.domain.ProductNotFoundException.class, () ->
                    locationInventoryService.addInventory(locationId, productId, 10, UUID.randomUUID(), null));

            verify(stockMovementService, never()).rejectIfCustomKujiParent(any());
        }

        @Test
        @DisplayName("should throw InvalidInventoryOperationException when inventory already exists at the location")
        void shouldThrowWhenInventoryAlreadyExists() {
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(testLocation));
            when(catalogEntityAccess.requireManagedProduct(productId)).thenReturn(testProduct);
            when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                    .thenReturn(Optional.of(testInventory));

            assertThrows(InvalidInventoryOperationException.class, () ->
                    locationInventoryService.addInventory(locationId, productId, 10, UUID.randomUUID(), null));

            verify(stockMovementService, never()).createInventoryWithTracking(
                    any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("findByProduct")
    class FindByProductTests {

        @Test
        @DisplayName("should return inventory entries for product")
        void shouldReturnInventoryForProduct() {
            when(locationInventoryRepository.findByProduct_Id(productId))
                    .thenReturn(List.of(testInventory));

            List<LocationInventory> result = locationInventoryService.findByProduct(productId);

            assertEquals(1, result.size());
            assertEquals(testInventory, result.get(0));
        }

        @Test
        @DisplayName("should return empty list when product has no inventory")
        void shouldReturnEmptyListWhenNoInventory() {
            when(locationInventoryRepository.findByProduct_Id(productId)).thenReturn(List.of());

            List<LocationInventory> result = locationInventoryService.findByProduct(productId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updateInventoryQuantity")
    class UpdateInventoryQuantityTests {

        @Test
        @DisplayName("should update inventory quantity")
        void shouldUpdateInventoryQuantity() {
            LocationInventory updatedInventory = LocationInventory.builder()
                    .id(inventoryId)
                    .location(testLocation)
                    .site(testSite)
                    .product(testProduct)
                    .quantity(20)
                    .build();

            when(locationInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));
            when(locationInventoryRepository.save(any(LocationInventory.class))).thenReturn(updatedInventory);

            LocationInventory result = locationInventoryService.updateInventoryQuantity(inventoryId, 20);

            assertEquals(20, result.getQuantity());
            verify(locationInventoryRepository).save(any(LocationInventory.class));
        }
    }

    @Nested
    @DisplayName("getTotalQuantityForProduct")
    class GetTotalQuantityTests {

        @Test
        @DisplayName("should return total quantity for product")
        void shouldReturnTotalQuantity() {
            when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(25);

            int result = locationInventoryService.getTotalQuantityForProduct(productId);

            assertEquals(25, result);
        }

        @Test
        @DisplayName("should return 0 when product has no inventory")
        void shouldReturnZeroWhenNoInventory() {
            when(locationInventoryRepository.sumQuantityByProductId(productId)).thenReturn(null);

            int result = locationInventoryService.getTotalQuantityForProduct(productId);

            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("findByLocationAndProduct")
    class FindByLocationAndProductTests {

        @Test
        @DisplayName("should return inventory when found")
        void shouldReturnInventoryWhenFound() {
            when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                    .thenReturn(Optional.of(testInventory));

            Optional<LocationInventory> result = locationInventoryService.findByLocationAndProduct(locationId, productId);

            assertTrue(result.isPresent());
            assertEquals(testInventory, result.get());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId))
                    .thenReturn(Optional.empty());

            Optional<LocationInventory> result = locationInventoryService.findByLocationAndProduct(locationId, productId);

            assertTrue(result.isEmpty());
        }
    }
}
