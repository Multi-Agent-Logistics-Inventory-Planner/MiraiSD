package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.DoubleClawMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DoubleClawMachineMapper {
    DoubleClawMachineResponseDTO toResponseDTO(DoubleClawMachine doubleClawMachine);
    List<DoubleClawMachineResponseDTO> toResponseDTOList(List<DoubleClawMachine> doubleClawMachines);
}

