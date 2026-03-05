package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.WindowResponseDTO;
import com.mirai.inventoryservice.models.storage.Window;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface WindowMapper {
    WindowResponseDTO toResponseDTO(Window window);
    List<WindowResponseDTO> toResponseDTOList(List<Window> windows);
}

