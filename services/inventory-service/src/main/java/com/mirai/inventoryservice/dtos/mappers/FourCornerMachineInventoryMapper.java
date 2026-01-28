package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.FourCornerMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.FourCornerMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface FourCornerMachineInventoryMapper {
    @Mapping(source = "fourCornerMachine.id", target = "fourCornerMachineId")
    @Mapping(source = "fourCornerMachine.fourCornerMachineCode", target = "fourCornerMachineCode")
    @Mapping(source = "item", target = "item")
    FourCornerMachineInventoryResponseDTO toResponseDTO(FourCornerMachineInventory inventory);

    List<FourCornerMachineInventoryResponseDTO> toResponseDTOList(List<FourCornerMachineInventory> inventories);
}
