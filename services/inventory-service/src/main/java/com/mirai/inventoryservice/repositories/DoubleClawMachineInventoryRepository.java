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
    List<DoubleClawMachineInventory> findByDoubleClawMachine_Id(UUID doubleClawMachineId);

    List<DoubleClawMachineInventory> findByItem_Id(UUID productId);

    Optional<DoubleClawMachineInventory> findByDoubleClawMachine_IdAndItem_Id(UUID doubleClawMachineId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM DoubleClawMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

