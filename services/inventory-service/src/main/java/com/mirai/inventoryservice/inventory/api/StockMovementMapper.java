package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.inventory.domain.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StockMovementMapper {
    @Mapping(source = "item.id", target = "itemId")
    StockMovementResponseDTO toResponseDTO(StockMovement stockMovement);

    List<StockMovementResponseDTO> toResponseDTOList(List<StockMovement> stockMovements);
}

