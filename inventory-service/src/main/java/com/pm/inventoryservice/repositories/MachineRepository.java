package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.Machine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MachineRepository extends JpaRepository<Machine, UUID> {
    boolean existsByMachineCode(String machineCode);
    boolean existsByMachineCodeAndIdNot(String machineCode, UUID machineId);
}
