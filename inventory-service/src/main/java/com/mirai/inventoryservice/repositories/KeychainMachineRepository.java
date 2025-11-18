package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.KeychainMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeychainMachineRepository extends JpaRepository<KeychainMachine, UUID> {
    Optional<KeychainMachine> findByKeychainMachineCode(String keychainMachineCode);
    boolean existsByKeychainMachineCode(String keychainMachineCode);
}

