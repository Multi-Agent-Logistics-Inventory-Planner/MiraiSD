package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.SingleClawMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SingleClawMachineInventoryMapper {
    @Mapping(source = "singleClawMachine.id", target = "singleClawMachineId")
    @Mapping(source = "singleClawMachine.singleClawMachineCode", target = "singleClawMachineCode")
    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.sku", target = "itemSku")
    @Mapping(source = "item.name", target = "itemName")
    @Mapping(source = "item.category", target = "itemCategory")
    SingleClawMachineInventoryResponseDTO toResponseDTO(SingleClawMachineInventory inventory);
    
    List<SingleClawMachineInventoryResponseDTO> toResponseDTOList(List<SingleClawMachineInventory> inventories);
}
