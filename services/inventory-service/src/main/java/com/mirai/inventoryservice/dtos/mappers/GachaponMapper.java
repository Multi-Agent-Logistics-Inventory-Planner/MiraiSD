package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.GachaponResponseDTO;
import com.mirai.inventoryservice.models.storage.Gachapon;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface GachaponMapper {
    GachaponResponseDTO toResponseDTO(Gachapon gachapon);
    List<GachaponResponseDTO> toResponseDTOList(List<Gachapon> gachapons);
}
