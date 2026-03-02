package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.SingleClawMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SingleClawMachineInventoryRepository extends JpaRepository<SingleClawMachineInventory, UUID> {
    @Query("SELECT i FROM SingleClawMachineInventory i JOIN FETCH i.singleClawMachine JOIN FETCH i.item WHERE i.singleClawMachine.id = :machineId")
    List<SingleClawMachineInventory> findBySingleClawMachine_Id(@Param("machineId") UUID machineId);

    @Query("SELECT i FROM SingleClawMachineInventory i JOIN FETCH i.singleClawMachine WHERE i.item.id = :productId")
    List<SingleClawMachineInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM SingleClawMachineInventory i JOIN FETCH i.singleClawMachine JOIN FETCH i.item WHERE i.singleClawMachine.id = :machineId AND i.item.id = :itemId")
    Optional<SingleClawMachineInventory> findBySingleClawMachine_IdAndItem_Id(@Param("machineId") UUID machineId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM SingleClawMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

