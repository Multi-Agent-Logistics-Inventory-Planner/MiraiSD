package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.PusherMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.PusherMachine;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PusherMachineMapper {
    PusherMachineResponseDTO toResponseDTO(PusherMachine pusherMachine);
    List<PusherMachineResponseDTO> toResponseDTOList(List<PusherMachine> pusherMachines);
}
