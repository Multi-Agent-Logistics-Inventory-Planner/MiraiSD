package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.FourCornerMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.FourCornerMachine;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FourCornerMachineMapper {
    FourCornerMachineResponseDTO toResponseDTO(FourCornerMachine fourCornerMachine);
    List<FourCornerMachineResponseDTO> toResponseDTOList(List<FourCornerMachine> fourCornerMachines);
}
