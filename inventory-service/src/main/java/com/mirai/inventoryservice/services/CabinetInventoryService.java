package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.CabinetInventoryNotFoundException;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import com.mirai.inventoryservice.models.storage.Cabinet;
import com.mirai.inventoryservice.repositories.CabinetInventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CabinetInventoryService {
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final CabinetService cabinetService;

    public CabinetInventoryService(
            CabinetInventoryRepository cabinetInventoryRepository,
            CabinetService cabinetService) {
        this.cabinetInventoryRepository = cabinetInventoryRepository;
        this.cabinetService = cabinetService;
    }

    public CabinetInventory addInventory(
            UUID cabinetId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        Cabinet cabinet = cabinetService.getCabinetById(cabinetId);
        
        // Subcategory is ONLY used for BLIND_BOX category, set to null for all others
        ProductSubcategory finalSubcategory = (category == ProductCategory.BLIND_BOX) ? subcategory : null;
        
        CabinetInventory inventory = CabinetInventory.builder()
                .cabinet(cabinet)
                .category(category)
                .subcategory(finalSubcategory)
                .description(description)
                .quantity(quantity)
                .build();
        
        return cabinetInventoryRepository.save(inventory);
    }

    public CabinetInventory getInventoryById(UUID inventoryId) {
        return cabinetInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new CabinetInventoryNotFoundException(
                        "Cabinet inventory not found with id: " + inventoryId));
    }

    public List<CabinetInventory> listInventory(UUID cabinetId) {
        // Verify cabinet exists
        cabinetService.getCabinetById(cabinetId);
        return cabinetInventoryRepository.findByCabinet_Id(cabinetId);
    }

    public CabinetInventory updateInventory(
            UUID inventoryId,
            ProductCategory category,
            ProductSubcategory subcategory,
            String description,
            Integer quantity) {
        
        CabinetInventory inventory = getInventoryById(inventoryId);
        
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
        
        return cabinetInventoryRepository.save(inventory);
    }

    public void deleteInventory(UUID inventoryId) {
        CabinetInventory inventory = getInventoryById(inventoryId);
        cabinetInventoryRepository.delete(inventory);
    }
}

