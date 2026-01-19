package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.KeychainMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.KeychainMachine;
import com.mirai.inventoryservice.repositories.KeychainMachineRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class KeychainMachineService {
    private final KeychainMachineRepository keychainMachineRepository;

    public KeychainMachineService(KeychainMachineRepository keychainMachineRepository) {
        this.keychainMachineRepository = keychainMachineRepository;
    }

    public KeychainMachine createKeychainMachine(String keychainMachineCode) {
        if (keychainMachineRepository.existsByKeychainMachineCode(keychainMachineCode)) {
            throw new DuplicateLocationCodeException("KeychainMachine with code already exists: " + keychainMachineCode);
        }
        KeychainMachine machine = KeychainMachine.builder()
                .keychainMachineCode(keychainMachineCode)
                .build();
        return keychainMachineRepository.save(machine);
    }

    public KeychainMachine getKeychainMachineById(UUID id) {
        return keychainMachineRepository.findById(id)
                .orElseThrow(() -> new KeychainMachineNotFoundException("KeychainMachine not found with id: " + id));
    }

    public KeychainMachine getKeychainMachineByCode(String code) {
        return keychainMachineRepository.findByKeychainMachineCode(code)
                .orElseThrow(() -> new KeychainMachineNotFoundException("KeychainMachine not found with code: " + code));
    }

    public List<KeychainMachine> getAllKeychainMachines() {
        return keychainMachineRepository.findAll();
    }

    public KeychainMachine updateKeychainMachine(UUID id, String keychainMachineCode) {
        KeychainMachine machine = getKeychainMachineById(id);
        if (!keychainMachineCode.equals(machine.getKeychainMachineCode()) && keychainMachineRepository.existsByKeychainMachineCode(keychainMachineCode)) {
            throw new DuplicateLocationCodeException("KeychainMachine with code already exists: " + keychainMachineCode);
        }
        machine.setKeychainMachineCode(keychainMachineCode);
        return keychainMachineRepository.save(machine);
    }

    public void deleteKeychainMachine(UUID id) {
        KeychainMachine machine = getKeychainMachineById(id);
        keychainMachineRepository.delete(machine);
    }

    public boolean existsByCode(String code) {
        return keychainMachineRepository.existsByKeychainMachineCode(code);
    }
}

