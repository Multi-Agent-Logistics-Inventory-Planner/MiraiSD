package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.CabinetResponseDTO;
import com.mirai.inventoryservice.models.storage.Cabinet;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CabinetMapper {
    CabinetResponseDTO toResponseDTO(Cabinet cabinet);
    List<CabinetResponseDTO> toResponseDTOList(List<Cabinet> cabinets);
}

