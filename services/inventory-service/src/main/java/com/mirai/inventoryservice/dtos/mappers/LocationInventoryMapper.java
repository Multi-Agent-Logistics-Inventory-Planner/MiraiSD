package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.LocationInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * Unified mapper for LocationInventory to response DTO.
 * Replaces the 9 type-specific mappers.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {ProductMapper.class})
public interface LocationInventoryMapper {
    @Mapping(source = "location.id", target = "locationId")
    @Mapping(source = "location.locationCode", target = "locationCode")
    @Mapping(source = "location.storageLocation.code", target = "storageLocationType")
    @Mapping(source = "product", target = "item")
    LocationInventoryResponseDTO toResponseDTO(LocationInventory inventory);

    List<LocationInventoryResponseDTO> toResponseDTOList(List<LocationInventory> inventories);
}
