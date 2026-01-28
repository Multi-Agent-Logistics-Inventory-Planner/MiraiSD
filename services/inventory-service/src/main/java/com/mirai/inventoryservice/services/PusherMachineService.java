package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.PusherMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.PusherMachine;
import com.mirai.inventoryservice.repositories.PusherMachineRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PusherMachineService {
    private final PusherMachineRepository pusherMachineRepository;

    public PusherMachineService(PusherMachineRepository pusherMachineRepository) {
        this.pusherMachineRepository = pusherMachineRepository;
    }

    public PusherMachine createPusherMachine(String pusherMachineCode) {
        if (pusherMachineRepository.existsByPusherMachineCode(pusherMachineCode)) {
            throw new DuplicateLocationCodeException("PusherMachine with code already exists: " + pusherMachineCode);
        }
        PusherMachine machine = PusherMachine.builder()
                .pusherMachineCode(pusherMachineCode)
                .build();
        return pusherMachineRepository.save(machine);
    }

    public PusherMachine getPusherMachineById(UUID id) {
        return pusherMachineRepository.findById(id)
                .orElseThrow(() -> new PusherMachineNotFoundException("PusherMachine not found with id: " + id));
    }

    public PusherMachine getPusherMachineByCode(String code) {
        return pusherMachineRepository.findByPusherMachineCode(code)
                .orElseThrow(() -> new PusherMachineNotFoundException("PusherMachine not found with code: " + code));
    }

    public List<PusherMachine> getAllPusherMachines() {
        return pusherMachineRepository.findAll();
    }

    public PusherMachine updatePusherMachine(UUID id, String pusherMachineCode) {
        PusherMachine machine = getPusherMachineById(id);
        if (!pusherMachineCode.equals(machine.getPusherMachineCode()) && pusherMachineRepository.existsByPusherMachineCode(pusherMachineCode)) {
            throw new DuplicateLocationCodeException("PusherMachine with code already exists: " + pusherMachineCode);
        }
        machine.setPusherMachineCode(pusherMachineCode);
        return pusherMachineRepository.save(machine);
    }

    public void deletePusherMachine(UUID id) {
        PusherMachine machine = getPusherMachineById(id);
        pusherMachineRepository.delete(machine);
    }

    public boolean existsByCode(String code) {
        return pusherMachineRepository.existsByPusherMachineCode(code);
    }
}
