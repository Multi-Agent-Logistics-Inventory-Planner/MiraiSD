package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.SingleClawMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SingleClawMachineMapper {
    SingleClawMachineResponseDTO toResponseDTO(SingleClawMachine singleClawMachine);
    List<SingleClawMachineResponseDTO> toResponseDTOList(List<SingleClawMachine> singleClawMachines);
}

