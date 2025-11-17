package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.MachineMapper;
import com.pm.inventoryservice.dtos.requests.MachineRequestDTO;
import com.pm.inventoryservice.dtos.responses.MachineResponseDTO;
import com.pm.inventoryservice.exceptions.MachineCodeAlreadyExistsException;
import com.pm.inventoryservice.exceptions.MachineNotFoundException;
import com.pm.inventoryservice.models.Machine;
import com.pm.inventoryservice.repositories.MachineRepository;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MachineService {
    private final MachineRepository machineRepository;

    public MachineService(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @Transactional(readOnly = true)
    public List<MachineResponseDTO> getAllMachines() {
        List<Machine> machines = machineRepository.findAll();
        return machines.stream().map(MachineMapper::toDTO).toList();
    }

    @Transactional
    public MachineResponseDTO createMachine(@NonNull MachineRequestDTO machineRequestDTO) {
        if(machineRepository.existsByMachineCode(machineRequestDTO.getMachineCode())){
            throw new MachineCodeAlreadyExistsException("A machine with this code already exists " + machineRequestDTO.getMachineCode());
        }
        Machine newMachine = machineRepository.save(MachineMapper.toEntity(machineRequestDTO));
        return MachineMapper.toDTO(newMachine);
    }

    @Transactional
    public MachineResponseDTO updateMachine(@NonNull UUID id, @NonNull MachineRequestDTO machineRequestDTO) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new MachineNotFoundException("Machine not found with ID: " + id));
        if(machineRepository.existsByMachineCodeAndIdNot(machineRequestDTO.getMachineCode(), id)) {
            throw new MachineCodeAlreadyExistsException("A machine with this code already exists " + machineRequestDTO.getMachineCode());
        }
        machine.setMachineCode(machineRequestDTO.getMachineCode());
        Machine updatedMachine = machineRepository.save(machine);
        return MachineMapper.toDTO(updatedMachine);
    }

    @Transactional
    public void deleteMachine(@NonNull UUID id) {
        machineRepository.findById(id)
                .orElseThrow(() -> new MachineNotFoundException("Machine not found with ID: " + id));
        machineRepository.deleteById(id);
    }
}
