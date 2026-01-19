package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.KeychainMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface KeychainMachineInventoryMapper {
    @Mapping(source = "keychainMachine.id", target = "keychainMachineId")
    @Mapping(source = "keychainMachine.keychainMachineCode", target = "keychainMachineCode")
    @Mapping(source = "item", target = "item")
    KeychainMachineInventoryResponseDTO toResponseDTO(KeychainMachineInventory inventory);

    List<KeychainMachineInventoryResponseDTO> toResponseDTOList(List<KeychainMachineInventory> inventories);
}

