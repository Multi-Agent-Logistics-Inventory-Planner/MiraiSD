package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.DoubleClawMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoubleClawMachineRepository extends JpaRepository<DoubleClawMachine, UUID> {
    Optional<DoubleClawMachine> findByDoubleClawMachineCode(String doubleClawMachineCode);
    boolean existsByDoubleClawMachineCode(String doubleClawMachineCode);
}

