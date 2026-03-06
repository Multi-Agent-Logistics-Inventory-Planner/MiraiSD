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
    @Query("SELECT i FROM KeychainMachineInventory i JOIN FETCH i.keychainMachine JOIN FETCH i.item WHERE i.keychainMachine.id = :machineId")
    List<KeychainMachineInventory> findByKeychainMachine_Id(@Param("machineId") UUID machineId);

    @Query("SELECT i FROM KeychainMachineInventory i JOIN FETCH i.keychainMachine WHERE i.item.id = :productId")
    List<KeychainMachineInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM KeychainMachineInventory i JOIN FETCH i.keychainMachine JOIN FETCH i.item WHERE i.keychainMachine.id = :machineId AND i.item.id = :itemId")
    Optional<KeychainMachineInventory> findByKeychainMachine_IdAndItem_Id(@Param("machineId") UUID machineId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM KeychainMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);

    void deleteByItem_Id(UUID productId);
}

