package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KeychainMachineInventoryRepository extends JpaRepository<KeychainMachineInventory, UUID> {
    List<KeychainMachineInventory> findByKeychainMachine_Id(UUID keychainMachineId);
}

