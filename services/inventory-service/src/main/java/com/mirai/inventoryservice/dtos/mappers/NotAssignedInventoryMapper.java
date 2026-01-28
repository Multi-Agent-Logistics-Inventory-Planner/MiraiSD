package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.NotAssignedInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.NotAssignedInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface NotAssignedInventoryMapper {
    @Mapping(source = "item", target = "item")
    NotAssignedInventoryResponseDTO toResponseDTO(NotAssignedInventory inventory);

    List<NotAssignedInventoryResponseDTO> toResponseDTOList(List<NotAssignedInventory> inventories);
}
