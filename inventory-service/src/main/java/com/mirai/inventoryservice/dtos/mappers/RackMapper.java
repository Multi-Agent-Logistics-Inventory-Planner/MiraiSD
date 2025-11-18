package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.RackResponseDTO;
import com.mirai.inventoryservice.models.storage.Rack;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RackMapper {
    RackResponseDTO toResponseDTO(Rack rack);
    List<RackResponseDTO> toResponseDTOList(List<Rack> racks);
}

