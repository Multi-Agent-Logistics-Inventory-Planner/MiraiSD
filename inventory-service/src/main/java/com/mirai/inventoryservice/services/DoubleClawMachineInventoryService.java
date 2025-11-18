package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DoubleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
import com.mirai.inventoryservice.repositories.DoubleClawMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DoubleClawMachineInventoryService {
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final DoubleClawMachineService doubleClawMachineService;

    public DoubleClawMachineInventoryService(
            DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository,
            DoubleClawMachineService doubleClawMachineService) {
        this.doubleClawMachineInventoryRepository = doubleClawMachineInventoryRepository;
        this.doubleClawMachineService = doubleClawMachineService;
    }

    public DoubleClawMachineInventory addInventory(
            UUID doubleClawMachineId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        DoubleClawMachine machine = doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId);
        
        // Subcategory is ONLY used for BLIND_BOX category, set to null for all others
        ProductSubcategory finalSubcategory = (category == ProductCategory.BLIND_BOX) ? subcategory : null;
        
        DoubleClawMachineInventory inventory = DoubleClawMachineInventory.builder()
                .doubleClawMachine(machine)
                .category(category)
                .subcategory(finalSubcategory)
                .description(description)
                .quantity(quantity)
                .build();
        
        return doubleClawMachineInventoryRepository.save(inventory);
    }

    public DoubleClawMachineInventory getInventoryById(UUID inventoryId) {
        return doubleClawMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new DoubleClawMachineInventoryNotFoundException(
                        "DoubleClawMachine inventory not found with id: " + inventoryId));
    }

    public List<DoubleClawMachineInventory> listInventory(UUID doubleClawMachineId) {
        // Verify machine exists
        doubleClawMachineService.getDoubleClawMachineById(doubleClawMachineId);
        return doubleClawMachineInventoryRepository.findByDoubleClawMachine_Id(doubleClawMachineId);
    }

    public DoubleClawMachineInventory updateInventory(
            UUID inventoryId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        DoubleClawMachineInventory inventory = getInventoryById(inventoryId);
        
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
        
        return doubleClawMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        DoubleClawMachineInventory inventory = getInventoryById(inventoryId);
        doubleClawMachineInventoryRepository.delete(inventory);
    }
}

