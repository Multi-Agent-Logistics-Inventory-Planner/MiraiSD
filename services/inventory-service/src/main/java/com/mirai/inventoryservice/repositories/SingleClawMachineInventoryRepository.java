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
    List<SingleClawMachineInventory> findBySingleClawMachine_Id(UUID singleClawMachineId);

    List<SingleClawMachineInventory> findByItem_Id(UUID productId);

    Optional<SingleClawMachineInventory> findBySingleClawMachine_IdAndItem_Id(UUID singleClawMachineId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM SingleClawMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

