package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.KeychainMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface KeychainMachineInventoryMapper {
    @Mapping(source = "keychainMachine.id", target = "keychainMachineId")
    @Mapping(source = "keychainMachine.keychainMachineCode", target = "keychainMachineCode")
    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.sku", target = "itemSku")
    @Mapping(source = "item.name", target = "itemName")
    @Mapping(source = "item.category", target = "itemCategory")
    KeychainMachineInventoryResponseDTO toResponseDTO(KeychainMachineInventory inventory);
    
    List<KeychainMachineInventoryResponseDTO> toResponseDTOList(List<KeychainMachineInventory> inventories);
}
