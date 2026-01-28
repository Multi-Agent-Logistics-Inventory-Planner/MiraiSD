package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.PusherMachineInventoryNotFoundException;
import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.inventory.PusherMachineInventory;
import com.mirai.inventoryservice.models.storage.PusherMachine;
import com.mirai.inventoryservice.repositories.PusherMachineInventoryRepository;
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
class PusherMachineInventoryServiceTest {

    @Mock
    private PusherMachineInventoryRepository pusherMachineInventoryRepository;

    @Mock
    private PusherMachineService pusherMachineService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private PusherMachineInventoryService pusherMachineInventoryService;

    private PusherMachine testMachine;
    private Product testProduct;
    private PusherMachineInventory testInventory;
    private UUID machineId;
    private UUID productId;
    private UUID inventoryId;

    @BeforeEach
    void setUp() {
        machineId = UUID.randomUUID();
        productId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();

        testMachine = PusherMachine.builder()
                .id(machineId)
                .pusherMachineCode("P1")
                .build();

        testProduct = Product.builder()
                .id(productId)
                .sku("SKU-001")
                .name("Test Product")
                .category(ProductCategory.PLUSHIE)
                .build();

        testInventory = PusherMachineInventory.builder()
                .id(inventoryId)
                .pusherMachine(testMachine)
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
            when(pusherMachineService.getPusherMachineById(machineId)).thenReturn(testMachine);
            when(productService.getProductById(productId)).thenReturn(testProduct);
            when(pusherMachineInventoryRepository.findByPusherMachine_IdAndItem_Id(machineId, productId))
                    .thenReturn(Optional.empty());
            when(pusherMachineInventoryRepository.save(any(PusherMachineInventory.class)))
                    .thenReturn(testInventory);

            PusherMachineInventory result = pusherMachineInventoryService.addInventory(machineId, productId, 10);

            assertNotNull(result);
            assertEquals(10, result.getQuantity());
            verify(pusherMachineInventoryRepository).save(any(PusherMachineInventory.class));
        }

        @Test
        @DisplayName("should throw InvalidInventoryOperationException when product already exists in machine")
        void shouldThrowExceptionWhenProductAlreadyExists() {
            when(pusherMachineService.getPusherMachineById(machineId)).thenReturn(testMachine);
            when(productService.getProductById(productId)).thenReturn(testProduct);
            when(pusherMachineInventoryRepository.findByPusherMachine_IdAndItem_Id(machineId, productId))
                    .thenReturn(Optional.of(testInventory));

            assertThrows(InvalidInventoryOperationException.class, () ->
                    pusherMachineInventoryService.addInventory(machineId, productId, 10));

            verify(pusherMachineInventoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getInventoryById")
    class GetInventoryByIdTests {

        @Test
        @DisplayName("should return inventory when found")
        void shouldReturnInventoryWhenFound() {
            when(pusherMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));

            PusherMachineInventory result = pusherMachineInventoryService.getInventoryById(inventoryId);

            assertEquals(testInventory, result);
        }

        @Test
        @DisplayName("should throw PusherMachineInventoryNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(pusherMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.empty());

            assertThrows(PusherMachineInventoryNotFoundException.class, () ->
                    pusherMachineInventoryService.getInventoryById(inventoryId));
        }
    }

    @Nested
    @DisplayName("listInventory")
    class ListInventoryTests {

        @Test
        @DisplayName("should return all inventory for machine")
        void shouldReturnAllInventoryForMachine() {
            PusherMachineInventory inventory2 = PusherMachineInventory.builder()
                    .id(UUID.randomUUID())
                    .pusherMachine(testMachine)
                    .item(Product.builder().id(UUID.randomUUID()).sku("SKU-002").name("Product 2").category(ProductCategory.PLUSHIE).build())
                    .quantity(5)
                    .build();

            when(pusherMachineService.getPusherMachineById(machineId)).thenReturn(testMachine);
            when(pusherMachineInventoryRepository.findByPusherMachine_Id(machineId))
                    .thenReturn(List.of(testInventory, inventory2));

            List<PusherMachineInventory> result = pusherMachineInventoryService.listInventory(machineId);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no inventory exists")
        void shouldReturnEmptyListWhenNoInventory() {
            when(pusherMachineService.getPusherMachineById(machineId)).thenReturn(testMachine);
            when(pusherMachineInventoryRepository.findByPusherMachine_Id(machineId))
                    .thenReturn(List.of());

            List<PusherMachineInventory> result = pusherMachineInventoryService.listInventory(machineId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByProduct")
    class FindByProductTests {

        @Test
        @DisplayName("should return inventory entries for product")
        void shouldReturnInventoryForProduct() {
            when(pusherMachineInventoryRepository.findByItem_Id(productId))
                    .thenReturn(List.of(testInventory));

            List<PusherMachineInventory> result = pusherMachineInventoryService.findByProduct(productId);

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
            PusherMachineInventory updatedInventory = PusherMachineInventory.builder()
                    .id(inventoryId)
                    .pusherMachine(testMachine)
                    .item(testProduct)
                    .quantity(20)
                    .build();

            when(pusherMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));
            when(pusherMachineInventoryRepository.save(any(PusherMachineInventory.class)))
                    .thenReturn(updatedInventory);

            PusherMachineInventory result = pusherMachineInventoryService.updateInventory(inventoryId, 20);

            assertEquals(20, result.getQuantity());
            verify(pusherMachineInventoryRepository).save(any(PusherMachineInventory.class));
        }

        @Test
        @DisplayName("should not update quantity when null")
        void shouldNotUpdateQuantityWhenNull() {
            when(pusherMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));
            when(pusherMachineInventoryRepository.save(any(PusherMachineInventory.class)))
                    .thenReturn(testInventory);

            PusherMachineInventory result = pusherMachineInventoryService.updateInventory(inventoryId, null);

            assertEquals(10, result.getQuantity());
        }
    }

    @Nested
    @DisplayName("deleteInventory")
    class DeleteInventoryTests {

        @Test
        @DisplayName("should delete inventory when found")
        void shouldDeleteInventoryWhenFound() {
            when(pusherMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.of(testInventory));

            pusherMachineInventoryService.deleteInventory(inventoryId);

            verify(pusherMachineInventoryRepository).delete(testInventory);
        }

        @Test
        @DisplayName("should throw PusherMachineInventoryNotFoundException when deleting non-existent inventory")
        void shouldThrowExceptionWhenDeletingNonExistent() {
            when(pusherMachineInventoryRepository.findById(inventoryId)).thenReturn(Optional.empty());

            assertThrows(PusherMachineInventoryNotFoundException.class, () ->
                    pusherMachineInventoryService.deleteInventory(inventoryId));

            verify(pusherMachineInventoryRepository, never()).delete(any());
        }
    }
}
