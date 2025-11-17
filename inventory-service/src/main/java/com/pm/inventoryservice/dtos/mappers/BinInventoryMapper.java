package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.BinInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.BinInventoryResponseDTO;
import com.pm.inventoryservice.models.Bin;
import com.pm.inventoryservice.models.BinInventory;
import org.springframework.lang.NonNull;

public final class BinInventoryMapper {

    private BinInventoryMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static BinInventoryResponseDTO toDTO(@NonNull BinInventory inventory) {
        BinInventoryResponseDTO dto = new BinInventoryResponseDTO();
        
        if (inventory.getId() != null) {
            dto.setId(inventory.getId().toString());
        }
        
        if (inventory.getBin() != null) {
            dto.setBinId(inventory.getBin().getId().toString());
            dto.setBinCode(inventory.getBin().getBinCode());
        }
        
        if (inventory.getCategory() != null) {
            dto.setCategory(inventory.getCategory().name());
        }
        
        dto.setDescription(inventory.getDescription());
        dto.setQuantity(inventory.getQuantity());
        dto.setCreatedAt(inventory.getCreatedAt());
        dto.setUpdatedAt(inventory.getUpdatedAt());
        
        return dto;
    }

    public static BinInventory toEntity(@NonNull BinInventoryRequestDTO dto, @NonNull Bin bin) {
        BinInventory inventory = new BinInventory();
        inventory.setBin(bin);
        inventory.setCategory(dto.getCategory());
        inventory.setDescription(dto.getDescription());
        inventory.setQuantity(dto.getQuantity());
        return inventory;
    }

    public static void updateEntityFromDTO(@NonNull BinInventory inventory, @NonNull BinInventoryRequestDTO dto) {
        inventory.setCategory(dto.getCategory());
        inventory.setDescription(dto.getDescription());
        inventory.setQuantity(dto.getQuantity());
    }
}

