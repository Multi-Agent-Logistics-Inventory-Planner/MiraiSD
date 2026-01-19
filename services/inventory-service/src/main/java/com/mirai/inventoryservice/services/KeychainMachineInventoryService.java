package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.KeychainMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import com.mirai.inventoryservice.models.storage.KeychainMachine;
import com.mirai.inventoryservice.repositories.KeychainMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class KeychainMachineInventoryService {
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;
    private final KeychainMachineService keychainMachineService;

    public KeychainMachineInventoryService(
            KeychainMachineInventoryRepository keychainMachineInventoryRepository,
            KeychainMachineService keychainMachineService) {
        this.keychainMachineInventoryRepository = keychainMachineInventoryRepository;
        this.keychainMachineService = keychainMachineService;
    }

    public KeychainMachineInventory addInventory(
            UUID keychainMachineId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        KeychainMachine machine = keychainMachineService.getKeychainMachineById(keychainMachineId);
        
        // Subcategory is ONLY used for BLIND_BOX category, set to null for all others
        ProductSubcategory finalSubcategory = (category == ProductCategory.BLIND_BOX) ? subcategory : null;
        
        KeychainMachineInventory inventory = KeychainMachineInventory.builder()
                .keychainMachine(machine)
                .category(category)
                .subcategory(finalSubcategory)
                .description(description)
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
        // Verify machine exists
        keychainMachineService.getKeychainMachineById(keychainMachineId);
        return keychainMachineInventoryRepository.findByKeychainMachine_Id(keychainMachineId);
    }

    public KeychainMachineInventory updateInventory(
            UUID inventoryId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        KeychainMachineInventory inventory = getInventoryById(inventoryId);
        
        if (category != null) {
            inventory.setCategory(category);
            // If category changed to non-BLIND_BOX, clear subcategory
            if (category != ProductCategory.BLIND_BOX) {
                inventory.setSubcategory(null);
            }
        }
        
        // Only set subcategory if category is BLIND_BOX
        if (subcategory != null && (category != null ? category : inventory.getCategory()) == ProductCategory.BLIND_BOX) {
            inventory.setSubcategory(subcategory);
        } else if ((category != null ? category : inventory.getCategory()) != ProductCategory.BLIND_BOX) {
            inventory.setSubcategory(null);
        }
        
        if (description != null) inventory.setDescription(description);
        if (quantity != null) inventory.setQuantity(quantity);
        
        return keychainMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        KeychainMachineInventory inventory = getInventoryById(inventoryId);
        keychainMachineInventoryRepository.delete(inventory);
    }
}

