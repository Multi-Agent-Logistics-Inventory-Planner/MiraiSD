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
    List<PusherMachineInventory> findByPusherMachine_Id(UUID pusherMachineId);

    List<PusherMachineInventory> findByItem_Id(UUID productId);

    Optional<PusherMachineInventory> findByPusherMachine_IdAndItem_Id(UUID pusherMachineId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM PusherMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}
