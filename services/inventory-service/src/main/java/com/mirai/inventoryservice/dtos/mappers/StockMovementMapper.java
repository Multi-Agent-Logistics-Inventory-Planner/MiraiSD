package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.StockMovementResponseDTO;
import com.mirai.inventoryservice.models.audit.StockMovement;
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

