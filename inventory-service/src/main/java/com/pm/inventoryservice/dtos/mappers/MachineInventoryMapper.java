package com.pm.inventoryservice.dtos.mappers;

import com.pm.inventoryservice.dtos.requests.MachineInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.MachineInventoryResponseDTO;
import com.pm.inventoryservice.models.Machine;
import com.pm.inventoryservice.models.MachineInventory;
import org.springframework.lang.NonNull;

public final class MachineInventoryMapper {

    private MachineInventoryMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static MachineInventoryResponseDTO toDTO(@NonNull MachineInventory inventory) {
        MachineInventoryResponseDTO dto = new MachineInventoryResponseDTO();
        
        if (inventory.getId() != null) {
            dto.setId(inventory.getId().toString());
        }
        
        if (inventory.getMachine() != null) {
            dto.setMachineId(inventory.getMachine().getId().toString());
            dto.setMachineCode(inventory.getMachine().getMachineCode());
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

    public static MachineInventory toEntity(@NonNull MachineInventoryRequestDTO dto, @NonNull Machine machine) {
        MachineInventory inventory = new MachineInventory();
        inventory.setMachine(machine);
        inventory.setCategory(dto.getCategory());
        inventory.setSubcategory(dto.getSubcategory());
        inventory.setDescription(dto.getDescription());
        inventory.setQuantity(dto.getQuantity());
        return inventory;
    }

    public static void updateEntityFromDTO(@NonNull MachineInventory inventory, @NonNull MachineInventoryRequestDTO dto) {
        inventory.setCategory(dto.getCategory());
        inventory.setSubcategory(dto.getSubcategory());
        inventory.setDescription(dto.getDescription());
        inventory.setQuantity(dto.getQuantity());
    }
}
