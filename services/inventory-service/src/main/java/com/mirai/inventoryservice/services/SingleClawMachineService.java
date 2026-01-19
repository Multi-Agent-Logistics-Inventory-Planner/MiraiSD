package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.SingleClawMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import com.mirai.inventoryservice.repositories.SingleClawMachineRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SingleClawMachineService {
    private final SingleClawMachineRepository singleClawMachineRepository;

    public SingleClawMachineService(SingleClawMachineRepository singleClawMachineRepository) {
        this.singleClawMachineRepository = singleClawMachineRepository;
    }

    public SingleClawMachine createSingleClawMachine(String singleClawMachineCode) {
        if (singleClawMachineRepository.existsBySingleClawMachineCode(singleClawMachineCode)) {
            throw new DuplicateLocationCodeException("SingleClawMachine with code already exists: " + singleClawMachineCode);
        }
        SingleClawMachine machine = SingleClawMachine.builder()
                .singleClawMachineCode(singleClawMachineCode)
                .build();
        return singleClawMachineRepository.save(machine);
    }

    public SingleClawMachine getSingleClawMachineById(UUID id) {
        return singleClawMachineRepository.findById(id)
                .orElseThrow(() -> new SingleClawMachineNotFoundException("SingleClawMachine not found with id: " + id));
    }

    public SingleClawMachine getSingleClawMachineByCode(String code) {
        return singleClawMachineRepository.findBySingleClawMachineCode(code)
                .orElseThrow(() -> new SingleClawMachineNotFoundException("SingleClawMachine not found with code: " + code));
    }

    public List<SingleClawMachine> getAllSingleClawMachines() {
        return singleClawMachineRepository.findAll();
    }

    public SingleClawMachine updateSingleClawMachine(UUID id, String singleClawMachineCode) {
        SingleClawMachine machine = getSingleClawMachineById(id);
        if (!singleClawMachineCode.equals(machine.getSingleClawMachineCode()) && singleClawMachineRepository.existsBySingleClawMachineCode(singleClawMachineCode)) {
            throw new DuplicateLocationCodeException("SingleClawMachine with code already exists: " + singleClawMachineCode);
        }
        machine.setSingleClawMachineCode(singleClawMachineCode);
        return singleClawMachineRepository.save(machine);
    }

    public void deleteSingleClawMachine(UUID id) {
        SingleClawMachine machine = getSingleClawMachineById(id);
        singleClawMachineRepository.delete(machine);
    }

    public boolean existsByCode(String code) {
        return singleClawMachineRepository.existsBySingleClawMachineCode(code);
    }
}

