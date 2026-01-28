package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.PusherMachineInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.PusherMachineInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface PusherMachineInventoryMapper {
    @Mapping(source = "pusherMachine.id", target = "pusherMachineId")
    @Mapping(source = "pusherMachine.pusherMachineCode", target = "pusherMachineCode")
    @Mapping(source = "item", target = "item")
    PusherMachineInventoryResponseDTO toResponseDTO(PusherMachineInventory inventory);

    List<PusherMachineInventoryResponseDTO> toResponseDTOList(List<PusherMachineInventory> inventories);
}
