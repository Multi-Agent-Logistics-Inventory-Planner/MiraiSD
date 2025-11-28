package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.CabinetInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CabinetInventoryMapper {
    @Mapping(source = "cabinet.id", target = "cabinetId")
    @Mapping(source = "cabinet.cabinetCode", target = "cabinetCode")
    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.sku", target = "itemSku")
    @Mapping(source = "item.name", target = "itemName")
    @Mapping(source = "item.category", target = "itemCategory")
    CabinetInventoryResponseDTO toResponseDTO(CabinetInventory inventory);
    
    List<CabinetInventoryResponseDTO> toResponseDTOList(List<CabinetInventory> inventories);
}
