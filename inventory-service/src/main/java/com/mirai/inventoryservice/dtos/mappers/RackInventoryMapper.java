package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.RackInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.RackInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RackInventoryMapper {
    @Mapping(source = "rack.id", target = "rackId")
    @Mapping(source = "rack.rackCode", target = "rackCode")
    RackInventoryResponseDTO toResponseDTO(RackInventory inventory);
    
    List<RackInventoryResponseDTO> toResponseDTOList(List<RackInventory> inventories);
}

