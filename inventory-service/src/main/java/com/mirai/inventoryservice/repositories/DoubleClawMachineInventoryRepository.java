package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DoubleClawMachineInventoryRepository extends JpaRepository<DoubleClawMachineInventory, UUID> {
    List<DoubleClawMachineInventory> findByDoubleClawMachine_Id(UUID doubleClawMachineId);
}

