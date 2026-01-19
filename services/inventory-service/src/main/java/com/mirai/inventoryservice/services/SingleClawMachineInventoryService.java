package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.SingleClawMachineInventoryNotFoundException;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import com.mirai.inventoryservice.repositories.SingleClawMachineInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SingleClawMachineInventoryService {
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final SingleClawMachineService singleClawMachineService;

    public SingleClawMachineInventoryService(
            SingleClawMachineInventoryRepository singleClawMachineInventoryRepository,
            SingleClawMachineService singleClawMachineService) {
        this.singleClawMachineInventoryRepository = singleClawMachineInventoryRepository;
        this.singleClawMachineService = singleClawMachineService;
    }

    public SingleClawMachineInventory addInventory(
            UUID singleClawMachineId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        SingleClawMachine machine = singleClawMachineService.getSingleClawMachineById(singleClawMachineId);
        
        // Subcategory is ONLY used for BLIND_BOX category, set to null for all others
        ProductSubcategory finalSubcategory = (category == ProductCategory.BLIND_BOX) ? subcategory : null;
        
        SingleClawMachineInventory inventory = SingleClawMachineInventory.builder()
                .singleClawMachine(machine)
                .category(category)
                .subcategory(finalSubcategory)
                .description(description)
                .quantity(quantity)
                .build();
        
        return singleClawMachineInventoryRepository.save(inventory);
    }

    public SingleClawMachineInventory getInventoryById(UUID inventoryId) {
        return singleClawMachineInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new SingleClawMachineInventoryNotFoundException(
                        "SingleClawMachine inventory not found with id: " + inventoryId));
    }

    public List<SingleClawMachineInventory> listInventory(UUID singleClawMachineId) {
        // Verify machine exists
        singleClawMachineService.getSingleClawMachineById(singleClawMachineId);
        return singleClawMachineInventoryRepository.findBySingleClawMachine_Id(singleClawMachineId);
    }

    public SingleClawMachineInventory updateInventory(
            UUID inventoryId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        SingleClawMachineInventory inventory = getInventoryById(inventoryId);
        
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
        
        return singleClawMachineInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        SingleClawMachineInventory inventory = getInventoryById(inventoryId);
        singleClawMachineInventoryRepository.delete(inventory);
    }
}

