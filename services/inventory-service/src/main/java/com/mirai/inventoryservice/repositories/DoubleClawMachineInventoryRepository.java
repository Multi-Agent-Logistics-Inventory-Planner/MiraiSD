package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.DoubleClawMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoubleClawMachineInventoryRepository extends JpaRepository<DoubleClawMachineInventory, UUID> {
    @Query("SELECT i FROM DoubleClawMachineInventory i JOIN FETCH i.doubleClawMachine JOIN FETCH i.item WHERE i.doubleClawMachine.id = :machineId")
    List<DoubleClawMachineInventory> findByDoubleClawMachine_Id(@Param("machineId") UUID machineId);

    @Query("SELECT i FROM DoubleClawMachineInventory i JOIN FETCH i.doubleClawMachine WHERE i.item.id = :productId")
    List<DoubleClawMachineInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM DoubleClawMachineInventory i JOIN FETCH i.doubleClawMachine JOIN FETCH i.item WHERE i.doubleClawMachine.id = :machineId AND i.item.id = :itemId")
    Optional<DoubleClawMachineInventory> findByDoubleClawMachine_IdAndItem_Id(@Param("machineId") UUID machineId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM DoubleClawMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);

    void deleteByItem_Id(UUID productId);
}

