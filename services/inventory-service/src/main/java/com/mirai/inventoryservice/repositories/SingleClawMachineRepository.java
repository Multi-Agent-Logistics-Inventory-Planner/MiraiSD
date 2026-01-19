package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.SingleClawMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SingleClawMachineRepository extends JpaRepository<SingleClawMachine, UUID> {
    Optional<SingleClawMachine> findBySingleClawMachineCode(String singleClawMachineCode);
    boolean existsBySingleClawMachineCode(String singleClawMachineCode);
}

