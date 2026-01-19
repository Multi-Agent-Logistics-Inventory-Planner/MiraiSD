package com.mirai.inventoryservice.dtos.mappers;

import com.mirai.inventoryservice.dtos.responses.KeychainMachineResponseDTO;
import com.mirai.inventoryservice.models.storage.KeychainMachine;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface KeychainMachineMapper {
    KeychainMachineResponseDTO toResponseDTO(KeychainMachine keychainMachine);
    List<KeychainMachineResponseDTO> toResponseDTOList(List<KeychainMachine> keychainMachines);
}

