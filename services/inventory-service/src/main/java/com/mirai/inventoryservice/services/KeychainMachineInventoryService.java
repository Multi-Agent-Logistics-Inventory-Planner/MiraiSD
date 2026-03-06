package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.KeychainMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import com.mirai.inventoryservice.repositories.KeychainMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class KeychainMachineInventoryService {
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;
    private final KeychainMachineService keychainMachineService;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public KeychainMachineInventoryService(
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            KeychainMachineService keychainMachineService,
            ProductService productService,
            StockMovementService stockMovementService) {
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.keychainMachineService = keychainMachineService;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    public KeychainMachineInventory addInventory(UUID keychainMachineId, UUID productId, Integer quantity, UUID actorId) {
        keychainMachineService.getKeychainMachineById(keychainMachineId); // Validate machine exists
        Product product = productService.getProductById(productId);

        Optional<KeychainMachineInventory> existing = keychainMachineInventoryRepository
                .findByKeychainMachine_IdAndItem_Id(keychainMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        UUID inventoryId = stockMovementService.createInventoryWithTracking(
                LocationType.KEYCHAIN_MACHINE, keychainMachineId, product, quantity,
                StockMovementReason.INITIAL_STOCK, actorId, null);

        return keychainMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new KeychainMachineInventoryNotFoundException("Failed to create inventory"));
    }

    public KeychainMachineInventory getInventoryById(UUID inventoryId) {
        return keychainMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new KeychainMachineInventoryNotFoundException(
                        "KeychainMachine inventory not found with id: " + inventoryId));
    }

    public List<KeychainMachineInventory> listInventory(UUID keychainMachineId) {
        keychainMachineService.getKeychainMachineById(keychainMachineId);
        return keychainMachineInventoryRepository.findByKeychainMachine_Id(keychainMachineId);
    }

    public List<KeychainMachineInventory> findByProduct(UUID productId) {
        return keychainMachineInventoryRepository.findByItem_Id(productId);
    }

    public KeychainMachineInventory updateInventory(UUID inventoryId, Integer quantity) {
        KeychainMachineInventory inventory = getInventoryById(inventoryId);
        if (quantity != null) {
            inventory.setQuantity(quantity);
        }
        return keychainMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        getInventoryById(inventoryId); // Validate exists
        stockMovementService.removeInventoryWithTracking(
                LocationType.KEYCHAIN_MACHINE, inventoryId,
                StockMovementReason.REMOVED, null, null);
    }
}
