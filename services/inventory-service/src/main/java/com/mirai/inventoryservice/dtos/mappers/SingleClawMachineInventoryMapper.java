package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.SingleClawMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface SingleClawMachineInventoryMapper {
    @Mapping(source = "singleClawMachine.id", target = "singleClawMachineId")
    @Mapping(source = "singleClawMachine.singleClawMachineCode", target = "singleClawMachineCode")
    @Mapping(source = "item", target = "item")
    SingleClawMachineInventoryResponseDTO toResponseDTO(SingleClawMachineInventory inventory);

    List<SingleClawMachineInventoryResponseDTO> toResponseDTOList(List<SingleClawMachineInventory> inventories);
}

