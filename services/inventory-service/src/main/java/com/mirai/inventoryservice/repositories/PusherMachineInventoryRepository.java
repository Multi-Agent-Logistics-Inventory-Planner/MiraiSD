package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.PusherMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PusherMachineInventoryRepository extends JpaRepository<PusherMachineInventory, UUID> {
    @Query("SELECT i FROM PusherMachineInventory i JOIN FETCH i.pusherMachine JOIN FETCH i.item WHERE i.pusherMachine.id = :machineId")
    List<PusherMachineInventory> findByPusherMachine_Id(@Param("machineId") UUID machineId);

    @Query("SELECT i FROM PusherMachineInventory i JOIN FETCH i.pusherMachine WHERE i.item.id = :productId")
    List<PusherMachineInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM PusherMachineInventory i JOIN FETCH i.pusherMachine JOIN FETCH i.item WHERE i.pusherMachine.id = :machineId AND i.item.id = :itemId")
    Optional<PusherMachineInventory> findByPusherMachine_IdAndItem_Id(@Param("machineId") UUID machineId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM PusherMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}
