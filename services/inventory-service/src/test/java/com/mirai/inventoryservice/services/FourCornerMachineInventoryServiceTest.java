package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.FourCornerMachineInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.inventory.FourCornerMachineInventory;
import com.mirai.inventoryservice.models.storage.FourCornerMachine;
import com.mirai.inventoryservice.repositories.FourCornerMachineInventoryRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FourCornerMachineInventoryServiceTest {

    @Mock
    private FourCornerMachineInventoryRepository fourCornerMachineInventoryRepository;

    @Mock
    private FourCornerMachineService fourCornerMachineService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private FourCornerMachineInventoryService fourCornerMachineInventoryService;

    private FourCornerMachine testMachine;
    private Product testProduct;
    private FourCornerMachineInventory testInventory;
    private UUID machineId;
    private UUID productId;
    private UUID inventoryId;

    @BeforeEach
    void setUp() {
        machineId = UUID.randomUUID();
        productId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();

        testMachine = FourCornerMachine.builder()
                .id(machineId)
                .fourCornerMachineCode("M1")
                .build();

        testProduct = Product.builder()
                .id(productId)
                .sku("SKU-001")
                .name("Test Product")
                .category(ProductCategory.PLUSHIE)
                .build();

        testInventory = FourCornerMachineInventory.builder()
                .id(inventoryId)
                .fourCornerMachine(testMachine)
                .item(testProduct)
                .quantity(10)
                .build();
    }

    @Nested
    @DisplayName("addInventory")
    class AddInventoryTests {

        @Test
        @DisplayName("should add inventory successfully")
        void shouldAddInventorySuccessfully() {
            when(fourCornerMachineService.getFourCornerMachineById(machineId)).thenReturn(testMachine);
            when(productService.getProductById(productId)).thenReturn(testProduct);
            when(fourCornerMachineInventoryRepository.findByFourCornerMachine_IdAndItem_Id(machineId, productId))
                    .thenReturn(Optional.empty());
            when(fourCornerMachineInventoryRepository.save(any(FourCornerMachineInventory.class)))
                    .thenReturn(testInventory);

            FourCornerMachineInventory result = fourCornerMachineInventoryService.addInventory(machineId, productId, 10);

            assertNotNull(result);
            assertEquals(10, result.getQuantity());
            verify(fourCornerMachineInventoryRepository).save(any(FourCornerMachineInventory.class));
        }

        @Test
        @DisplayName("should throw InvalidInventoryOperationException when product already exists in machine")
        void shouldThrowExceptionWhenProductAlreadyExists() {
            when(fourCornerMachineService.getFourCornerMachineById(machineId)).thenReturn(testMachine);
            when(productService.getProductById(productId)).thenReturn(testProduct);
            when(fourCornerMachineInventoryRepository.findByFourCornerMachine_IdAndItem_Id(machineId, productId))
                    .thenReturn(Optional.of(testInventory));

            assertThrows(InvalidInventoryOperationException.class, () ->
                    fourCornerMachineInventoryService.addInventory(machineId, productId, 10));

            verify(fourCornerMachineInventoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getInventoryById")
    class GetInventoryByIdTests {

        @Test
        @DisplayName("should return inventory when found")
        void shouldReturnInventoryWhenFound() {
            when(fourCornerMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));

            FourCornerMachineInventory result = fourCornerMachineInventoryService.getInventoryById(inventoryId);

            assertEquals(testInventory, result);
        }

        @Test
        @DisplayName("should throw FourCornerMachineInventoryNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(fourCornerMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.empty());

            assertThrows(FourCornerMachineInventoryNotFoundException.class, () ->
                    fourCornerMachineInventoryService.getInventoryById(inventoryId));
        }
    }

    @Nested
    @DisplayName("listInventory")
    class ListInventoryTests {

        @Test
        @DisplayName("should return all inventory for machine")
        void shouldReturnAllInventoryForMachine() {
            FourCornerMachineInventory inventory2 = FourCornerMachineInventory.builder()
                    .id(UUID.randomUUID())
                    .fourCornerMachine(testMachine)
                    .item(Product.builder().id(UUID.randomUUID()).sku("SKU-002").name("Product 2").category(ProductCategory.PLUSHIE).build())
                    .quantity(5)
                    .build();

            when(fourCornerMachineService.getFourCornerMachineById(machineId)).thenReturn(testMachine);
            when(fourCornerMachineInventoryRepository.findByFourCornerMachine_Id(machineId))
                    .thenReturn(List.of(testInventory, inventory2));

            List<FourCornerMachineInventory> result = fourCornerMachineInventoryService.listInventory(machineId);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no inventory exists")
        void shouldReturnEmptyListWhenNoInventory() {
            when(fourCornerMachineService.getFourCornerMachineById(machineId)).thenReturn(testMachine);
            when(fourCornerMachineInventoryRepository.findByFourCornerMachine_Id(machineId))
                    .thenReturn(List.of());

            List<FourCornerMachineInventory> result = fourCornerMachineInventoryService.listInventory(machineId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByProduct")
    class FindByProductTests {

        @Test
        @DisplayName("should return inventory entries for product")
        void shouldReturnInventoryForProduct() {
            when(fourCornerMachineInventoryRepository.findByItem_Id(productId))
                    .thenReturn(List.of(testInventory));

            List<FourCornerMachineInventory> result = fourCornerMachineInventoryService.findByProduct(productId);

            assertEquals(1, result.size());
            assertEquals(testInventory, result.get(0));
        }
    }

    @Nested
    @DisplayName("updateInventory")
    class UpdateInventoryTests {

        @Test
        @DisplayName("should update inventory quantity")
        void shouldUpdateInventoryQuantity() {
            FourCornerMachineInventory updatedInventory = FourCornerMachineInventory.builder()
                    .id(inventoryId)
                    .fourCornerMachine(testMachine)
                    .item(testProduct)
                    .quantity(20)
                    .build();

            when(fourCornerMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));
            when(fourCornerMachineInventoryRepository.save(any(FourCornerMachineInventory.class)))
                    .thenReturn(updatedInventory);

            FourCornerMachineInventory result = fourCornerMachineInventoryService.updateInventory(inventoryId, 20);

            assertEquals(20, result.getQuantity());
            verify(fourCornerMachineInventoryRepository).save(any(FourCornerMachineInventory.class));
        }

        @Test
        @DisplayName("should not update quantity when null")
        void shouldNotUpdateQuantityWhenNull() {
            when(fourCornerMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));
            when(fourCornerMachineInventoryRepository.save(any(FourCornerMachineInventory.class)))
                    .thenReturn(testInventory);

            FourCornerMachineInventory result = fourCornerMachineInventoryService.updateInventory(inventoryId, null);

            assertEquals(10, result.getQuantity());
        }
    }

    @Nested
    @DisplayName("deleteInventory")
    class DeleteInventoryTests {

        @Test
        @DisplayName("should delete inventory when found")
        void shouldDeleteInventoryWhenFound() {
            when(fourCornerMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));

            fourCornerMachineInventoryService.deleteInventory(inventoryId);

            verify(fourCornerMachineInventoryRepository).delete(testInventory);
        }

        @Test
        @DisplayName("should throw FourCornerMachineInventoryNotFoundException when deleting non-existent inventory")
        void shouldThrowExceptionWhenDeletingNonExistent() {
            when(fourCornerMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.empty());

            assertThrows(FourCornerMachineInventoryNotFoundException.class, () ->
                    fourCornerMachineInventoryService.deleteInventory(inventoryId));

            verify(fourCornerMachineInventoryRepository, never()).delete(any());
        }
    }
}
