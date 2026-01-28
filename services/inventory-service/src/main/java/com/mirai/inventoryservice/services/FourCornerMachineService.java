package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.FourCornerMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.FourCornerMachine;
import com.mirai.inventoryservice.repositories.FourCornerMachineRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FourCornerMachineService {
    private final FourCornerMachineRepository fourCornerMachineRepository;

    public FourCornerMachineService(FourCornerMachineRepository fourCornerMachineRepository) {
        this.fourCornerMachineRepository = fourCornerMachineRepository;
    }

    public FourCornerMachine createFourCornerMachine(String fourCornerMachineCode) {
        if (fourCornerMachineRepository.existsByFourCornerMachineCode(fourCornerMachineCode)) {
            throw new DuplicateLocationCodeException("FourCornerMachine with code already exists: " + fourCornerMachineCode);
        }
        FourCornerMachine machine = FourCornerMachine.builder()
                .fourCornerMachineCode(fourCornerMachineCode)
                .build();
        return fourCornerMachineRepository.save(machine);
    }

    public FourCornerMachine getFourCornerMachineById(UUID id) {
        return fourCornerMachineRepository.findById(id)
                .orElseThrow(() -> new FourCornerMachineNotFoundException("FourCornerMachine not found with id: " + id));
    }

    public FourCornerMachine getFourCornerMachineByCode(String code) {
        return fourCornerMachineRepository.findByFourCornerMachineCode(code)
                .orElseThrow(() -> new FourCornerMachineNotFoundException("FourCornerMachine not found with code: " + code));
    }

    public List<FourCornerMachine> getAllFourCornerMachines() {
        return fourCornerMachineRepository.findAll();
    }

    public FourCornerMachine updateFourCornerMachine(UUID id, String fourCornerMachineCode) {
        FourCornerMachine machine = getFourCornerMachineById(id);
        if (!fourCornerMachineCode.equals(machine.getFourCornerMachineCode()) && fourCornerMachineRepository.existsByFourCornerMachineCode(fourCornerMachineCode)) {
            throw new DuplicateLocationCodeException("FourCornerMachine with code already exists: " + fourCornerMachineCode);
        }
        machine.setFourCornerMachineCode(fourCornerMachineCode);
        return fourCornerMachineRepository.save(machine);
    }

    public void deleteFourCornerMachine(UUID id) {
        FourCornerMachine machine = getFourCornerMachineById(id);
        fourCornerMachineRepository.delete(machine);
    }

    public boolean existsByCode(String code) {
        return fourCornerMachineRepository.existsByFourCornerMachineCode(code);
    }
}
