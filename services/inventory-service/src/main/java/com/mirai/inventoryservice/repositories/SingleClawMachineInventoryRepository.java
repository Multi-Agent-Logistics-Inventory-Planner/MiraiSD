package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SingleClawMachineInventoryRepository extends JpaRepository<SingleClawMachineInventory, UUID> {
    List<SingleClawMachineInventory> findBySingleClawMachine_Id(UUID singleClawMachineId);
}

