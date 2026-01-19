package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.BoxBinInventoryNotFoundException;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BoxBinInventoryService {
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final BoxBinService boxBinService;

    public BoxBinInventoryService(
            BoxBinInventoryRepository boxBinInventoryRepository,
            BoxBinService boxBinService) {
        this.boxBinInventoryRepository = boxBinInventoryRepository;
        this.boxBinService = boxBinService;
    }

    public BoxBinInventory addInventory(
            UUID boxBinId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        BoxBin boxBin = boxBinService.getBoxBinById(boxBinId);
        
        // Subcategory is ONLY used for BLIND_BOX category, set to null for all others
        ProductSubcategory finalSubcategory = (category == ProductCategory.BLIND_BOX) ? subcategory : null;
        
        BoxBinInventory inventory = BoxBinInventory.builder()
                .boxBin(boxBin)
                .category(category)
                .subcategory(finalSubcategory)
                .description(description)
                .quantity(quantity)
                .build();
        
        return boxBinInventoryRepository.save(inventory);
    }

    public BoxBinInventory getInventoryById(UUID inventoryId) {
        return boxBinInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new BoxBinInventoryNotFoundException(
                        "BoxBin inventory not found with id: " + inventoryId));
    }

    public List<BoxBinInventory> listInventory(UUID boxBinId) {
        // Verify box bin exists
        boxBinService.getBoxBinById(boxBinId);
        return boxBinInventoryRepository.findByBoxBin_Id(boxBinId);
    }

    public BoxBinInventory updateInventory(
            UUID inventoryId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        BoxBinInventory inventory = getInventoryById(inventoryId);
        
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
        
        return boxBinInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        BoxBinInventory inventory = getInventoryById(inventoryId);
        boxBinInventoryRepository.delete(inventory);
    }
}

