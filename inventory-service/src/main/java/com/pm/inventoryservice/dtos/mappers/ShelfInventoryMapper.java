package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.ShelfInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.ShelfInventoryResponseDTO;
import com.pm.inventoryservice.models.Shelf;
import com.pm.inventoryservice.models.ShelfInventory;
import org.springframework.lang.NonNull;

public final class ShelfInventoryMapper {

    private ShelfInventoryMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ShelfInventoryResponseDTO toDTO(@NonNull ShelfInventory inventory) {
        ShelfInventoryResponseDTO dto = new ShelfInventoryResponseDTO();
        
        if (inventory.getId() != null) {
            dto.setId(inventory.getId().toString());
        }
        
        if (inventory.getShelf() != null) {
            dto.setShelfId(inventory.getShelf().getId().toString());
            dto.setShelfCode(inventory.getShelf().getShelfCode());
        }
        
        if (inventory.getCategory() != null) {
            dto.setCategory(inventory.getCategory().name());
        }
        
        if (inventory.getSubcategory() != null) {
            dto.setSubcategory(inventory.getSubcategory().name());
        }
        
        dto.setDescription(inventory.getDescription());
        dto.setQuantity(inventory.getQuantity());
        dto.setCreatedAt(inventory.getCreatedAt());
        dto.setUpdatedAt(inventory.getUpdatedAt());
        
        return dto;
    }

    public static ShelfInventory toEntity(@NonNull ShelfInventoryRequestDTO dto, @NonNull Shelf shelf) {
        ShelfInventory inventory = new ShelfInventory();
        inventory.setShelf(shelf);
        inventory.setCategory(dto.getCategory());
        inventory.setSubcategory(dto.getSubcategory());
        inventory.setDescription(dto.getDescription());
        inventory.setQuantity(dto.getQuantity());
        return inventory;
    }

    public static void updateEntityFromDTO(@NonNull ShelfInventory inventory, @NonNull ShelfInventoryRequestDTO dto) {
        inventory.setCategory(dto.getCategory());
        inventory.setSubcategory(dto.getSubcategory());
        inventory.setDescription(dto.getDescription());
        inventory.setQuantity(dto.getQuantity());
    }
}

