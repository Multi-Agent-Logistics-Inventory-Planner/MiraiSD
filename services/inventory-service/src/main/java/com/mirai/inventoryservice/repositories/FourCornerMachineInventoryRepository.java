package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.FourCornerMachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FourCornerMachineInventoryRepository extends JpaRepository<FourCornerMachineInventory, UUID> {
    @Query("SELECT i FROM FourCornerMachineInventory i JOIN FETCH i.fourCornerMachine JOIN FETCH i.item WHERE i.fourCornerMachine.id = :machineId")
    List<FourCornerMachineInventory> findByFourCornerMachine_Id(@Param("machineId") UUID machineId);

    @Query("SELECT i FROM FourCornerMachineInventory i JOIN FETCH i.fourCornerMachine WHERE i.item.id = :productId")
    List<FourCornerMachineInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM FourCornerMachineInventory i JOIN FETCH i.fourCornerMachine JOIN FETCH i.item WHERE i.fourCornerMachine.id = :machineId AND i.item.id = :itemId")
    Optional<FourCornerMachineInventory> findByFourCornerMachine_IdAndItem_Id(@Param("machineId") UUID machineId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM FourCornerMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}
