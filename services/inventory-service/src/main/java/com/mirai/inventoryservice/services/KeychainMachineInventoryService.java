package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.mirai.inventoryservice.exceptions.KeychainMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import com.mirai.inventoryservice.models.storage.KeychainMachine;
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

    public KeychainMachineInventoryService(
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            KeychainMachineService keychainMachineService,
            ProductService productService) {
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.keychainMachineService = keychainMachineService;
        this.productService = productService;
    }

    public KeychainMachineInventory addInventory(UUID keychainMachineId, UUID productId, Integer quantity) {
        KeychainMachine machine = keychainMachineService.getKeychainMachineById(keychainMachineId);
        Product product = productService.getProductById(productId);

        Optional<KeychainMachineInventory> existing = keychainMachineInventoryRepository
                .findByKeychainMachine_IdAndItem_Id(keychainMachineId, productId);
        if (existing.isPresent()) {
            throw new InvalidInventoryOperationException(
                    "Inventory for product " + product.getSku() + " already exists in this machine");
        }

        KeychainMachineInventory inventory = KeychainMachineInventory.builder()
                .keychainMachine(machine)
                .item(product)
                .quantity(quantity)
                .build();

        return keychainMachineInventoryRepository.save(inventory);
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
        KeychainMachineInventory inventory = getInventoryById(inventoryId);
        keychainMachineInventoryRepository.delete(inventory);
    }
}
