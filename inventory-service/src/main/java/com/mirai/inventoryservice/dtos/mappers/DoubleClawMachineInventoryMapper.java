package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.DoubleClawMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DoubleClawMachineInventoryMapper {
    @Mapping(source = "doubleClawMachine.id", target = "doubleClawMachineId")
    @Mapping(source = "doubleClawMachine.doubleClawMachineCode", target = "doubleClawMachineCode")
    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.sku", target = "itemSku")
    @Mapping(source = "item.name", target = "itemName")
    @Mapping(source = "item.category", target = "itemCategory")
    DoubleClawMachineInventoryResponseDTO toResponseDTO(DoubleClawMachineInventory inventory);
    
    List<DoubleClawMachineInventoryResponseDTO> toResponseDTOList(List<DoubleClawMachineInventory> inventories);
}
