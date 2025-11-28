package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.BoxBinInventoryResponseDTO;
import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BoxBinInventoryMapper {
    @Mapping(source = "boxBin.id", target = "boxBinId")
    @Mapping(source = "boxBin.boxBinCode", target = "boxBinCode")
    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.sku", target = "itemSku")
    @Mapping(source = "item.name", target = "itemName")
    @Mapping(source = "item.category", target = "itemCategory")
    BoxBinInventoryResponseDTO toResponseDTO(BoxBinInventory inventory);
    
    List<BoxBinInventoryResponseDTO> toResponseDTOList(List<BoxBinInventory> inventories);
}

