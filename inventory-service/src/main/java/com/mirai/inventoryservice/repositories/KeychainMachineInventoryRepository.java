package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.KeychainMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeychainMachineInventoryRepository extends JpaRepository<KeychainMachineInventory, UUID> {
    List<KeychainMachineInventory> findByKeychainMachine_Id(UUID keychainMachineId);

    List<KeychainMachineInventory> findByItem_Id(UUID productId);

    Optional<KeychainMachineInventory> findByKeychainMachine_IdAndItem_Id(UUID keychainMachineId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM KeychainMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

