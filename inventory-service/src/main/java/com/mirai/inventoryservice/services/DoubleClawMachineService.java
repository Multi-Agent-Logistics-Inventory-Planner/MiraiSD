package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DoubleClawMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
import com.mirai.inventoryservice.repositories.DoubleClawMachineRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DoubleClawMachineService {
    private final DoubleClawMachineRepository doubleClawMachineRepository;

    public DoubleClawMachineService(DoubleClawMachineRepository doubleClawMachineRepository) {
        this.doubleClawMachineRepository = doubleClawMachineRepository;
    }

    public DoubleClawMachine createDoubleClawMachine(String doubleClawMachineCode) {
        DoubleClawMachine machine = DoubleClawMachine.builder()
                .doubleClawMachineCode(doubleClawMachineCode)
                .build();
        return doubleClawMachineRepository.save(machine);
    }

    public DoubleClawMachine getDoubleClawMachineById(UUID id) {
        return doubleClawMachineRepository.findById(id)
                .orElseThrow(() -> new DoubleClawMachineNotFoundException("DoubleClawMachine not found with id: " + id));
    }

    public DoubleClawMachine getDoubleClawMachineByCode(String code) {
        return doubleClawMachineRepository.findByDoubleClawMachineCode(code)
                .orElseThrow(() -> new DoubleClawMachineNotFoundException("DoubleClawMachine not found with code: " + code));
    }

    public List<DoubleClawMachine> getAllDoubleClawMachines() {
        return doubleClawMachineRepository.findAll();
    }

    public DoubleClawMachine updateDoubleClawMachine(UUID id, String doubleClawMachineCode) {
        DoubleClawMachine machine = getDoubleClawMachineById(id);
        machine.setDoubleClawMachineCode(doubleClawMachineCode);
        return doubleClawMachineRepository.save(machine);
    }

    public void deleteDoubleClawMachine(UUID id) {
        DoubleClawMachine machine = getDoubleClawMachineById(id);
        doubleClawMachineRepository.delete(machine);
    }

    public boolean existsByCode(String code) {
        return doubleClawMachineRepository.existsByDoubleClawMachineCode(code);
    }
}

