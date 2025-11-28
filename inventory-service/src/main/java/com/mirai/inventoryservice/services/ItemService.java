package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.requests.ItemRequestDTO;
import com.mirai.inventoryservice.exceptions.ItemNotFoundException;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.repositories.ItemRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class ItemService {
    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public Item createItem(ItemRequestDTO requestDTO) {
        // Validate subcategory only for BLIND_BOX
        if (requestDTO.getCategory() == ProductCategory.BLIND_BOX && requestDTO.getSubcategory() == null) {
            throw new IllegalArgumentException("Subcategory is required for BLIND_BOX category");
        }
        
        // Clear subcategory if not BLIND_BOX
        ProductSubcategory finalSubcategory = 
            (requestDTO.getCategory() == ProductCategory.BLIND_BOX) ? requestDTO.getSubcategory() : null;

        Item item = Item.builder()
                .sku(requestDTO.getSku())
                .category(requestDTO.getCategory())
                .subcategory(finalSubcategory)
                .name(requestDTO.getName())
                .description(requestDTO.getDescription())
                .reorderPoint(requestDTO.getReorderPoint() != null ? requestDTO.getReorderPoint() : 10)
                .targetStockLevel(requestDTO.getTargetStockLevel() != null ? requestDTO.getTargetStockLevel() : 50)
                .leadTimeDays(requestDTO.getLeadTimeDays() != null ? requestDTO.getLeadTimeDays() : 7)
                .unitCost(requestDTO.getUnitCost())
                .isActive(requestDTO.getIsActive() != null ? requestDTO.getIsActive() : true)
                .imageUrl(requestDTO.getImageUrl())
                .notes(requestDTO.getNotes())
                .build();

        Item saved = itemRepository.save(item);
        log.info("Created item: {} ({})", saved.getName(), saved.getId());
        return saved;
    }

    public Item getItemById(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found with id: " + id));
    }

    public Item getItemBySku(String sku) {
        return itemRepository.findBySku(sku)
                .orElseThrow(() -> new ItemNotFoundException("Item not found with SKU: " + sku));
    }

    public Page<Item> listItems(Pageable pageable) {
        return itemRepository.findAll(pageable);
    }

    public Page<Item> listItems(ProductCategory category, Boolean activeOnly, Pageable pageable) {
        if (category != null && Boolean.TRUE.equals(activeOnly)) {
            return itemRepository.findByCategoryAndIsActiveTrue(category, pageable);
        } else if (category != null) {
            return itemRepository.findByCategory(category, pageable);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            return itemRepository.findByIsActiveTrue(pageable);
        } else {
            return itemRepository.findAll(pageable);
        }
    }

    public List<Item> listActiveItems() {
        return itemRepository.findByIsActiveTrue();
    }

    public Item updateItem(UUID id, ItemRequestDTO requestDTO) {
        Item item = getItemById(id);

        if (requestDTO.getSku() != null) item.setSku(requestDTO.getSku());
        if (requestDTO.getCategory() != null) {
            item.setCategory(requestDTO.getCategory());
            // Clear subcategory if not BLIND_BOX
            if (requestDTO.getCategory() != ProductCategory.BLIND_BOX) {
                item.setSubcategory(null);
            }
        }
        
        // Only set subcategory if category is BLIND_BOX
        if (requestDTO.getSubcategory() != null) {
            if (item.getCategory() == ProductCategory.BLIND_BOX) {
                item.setSubcategory(requestDTO.getSubcategory());
            }
        }
        
        if (requestDTO.getName() != null) item.setName(requestDTO.getName());
        if (requestDTO.getDescription() != null) item.setDescription(requestDTO.getDescription());
        if (requestDTO.getReorderPoint() != null) item.setReorderPoint(requestDTO.getReorderPoint());
        if (requestDTO.getTargetStockLevel() != null) item.setTargetStockLevel(requestDTO.getTargetStockLevel());
        if (requestDTO.getLeadTimeDays() != null) item.setLeadTimeDays(requestDTO.getLeadTimeDays());
        if (requestDTO.getUnitCost() != null) item.setUnitCost(requestDTO.getUnitCost());
        if (requestDTO.getIsActive() != null) item.setIsActive(requestDTO.getIsActive());
        if (requestDTO.getImageUrl() != null) item.setImageUrl(requestDTO.getImageUrl());
        if (requestDTO.getNotes() != null) item.setNotes(requestDTO.getNotes());

        Item updated = itemRepository.save(item);
        log.info("Updated item: {} ({})", updated.getName(), updated.getId());
        return updated;
    }

    public void deactivateItem(UUID id) {
        Item item = getItemById(id);
        item.setIsActive(false);
        itemRepository.save(item);
        log.info("Deactivated item: {} ({})", item.getName(), item.getId());
    }

    /**
     * Find existing item or create new one based on key attributes.
     * Used for auto-creating items during inventory operations.
     */
    public Item findOrCreateItem(
            ProductCategory category,
            ProductSubcategory subcategory,
            String name,
            String description) {
        
        // Try to find existing item
        return itemRepository.findByCategoryAndNameAndDescription(category, name, description)
                .orElseGet(() -> {
                    log.info("Auto-creating item: {} ({})", name, category);
                    ItemRequestDTO dto = ItemRequestDTO.builder()
                            .category(category)
                            .subcategory(subcategory)
                            .name(name)
                            .description(description)
                            .build();
                    return createItem(dto);
                });
    }
}

