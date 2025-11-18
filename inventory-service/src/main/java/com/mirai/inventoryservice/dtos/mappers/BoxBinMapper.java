package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.BoxBinResponseDTO;
import com.mirai.inventoryservice.models.storage.BoxBin;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BoxBinMapper {
    BoxBinResponseDTO toResponseDTO(BoxBin boxBin);
    List<BoxBinResponseDTO> toResponseDTOList(List<BoxBin> boxBins);
}

