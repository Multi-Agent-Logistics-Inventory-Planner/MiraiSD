package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.RackInventoryNotFoundException;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.models.inventory.RackInventory;
import com.mirai.inventoryservice.models.storage.Rack;
import com.mirai.inventoryservice.repositories.RackInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RackInventoryService {
    private final RackInventoryRepository rackInventoryRepository;
    private final RackService rackService;

    public RackInventoryService(
            RackInventoryRepository rackInventoryRepository,
            RackService rackService) {
        this.rackInventoryRepository = rackInventoryRepository;
        this.rackService = rackService;
    }

    public RackInventory addInventory(
            UUID rackId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        Rack rack = rackService.getRackById(rackId);
        
        // Subcategory is ONLY used for BLIND_BOX category, set to null for all others
        ProductSubcategory finalSubcategory = (category == ProductCategory.BLIND_BOX) ? subcategory : null;
        
        RackInventory inventory = RackInventory.builder()
                .rack(rack)
                .category(category)
                .subcategory(finalSubcategory)
                .description(description)
                .quantity(quantity)
                .build();
        
        return rackInventoryRepository.save(inventory);
    }

    public RackInventory getInventoryById(UUID inventoryId) {
        return rackInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RackInventoryNotFoundException(
                        "Rack inventory not found with id: " + inventoryId));
    }

    public List<RackInventory> listInventory(UUID rackId) {
        // Verify rack exists
        rackService.getRackById(rackId);
        return rackInventoryRepository.findByRack_Id(rackId);
    }

    public RackInventory updateInventory(
            UUID inventoryId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        RackInventory inventory = getInventoryById(inventoryId);
        
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
        
        return rackInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        RackInventory inventory = getInventoryById(inventoryId);
        rackInventoryRepository.delete(inventory);
    }
}

