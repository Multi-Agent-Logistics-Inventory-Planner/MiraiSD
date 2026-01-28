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
    List<FourCornerMachineInventory> findByFourCornerMachine_Id(UUID fourCornerMachineId);

    List<FourCornerMachineInventory> findByItem_Id(UUID productId);

    Optional<FourCornerMachineInventory> findByFourCornerMachine_IdAndItem_Id(UUID fourCornerMachineId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM FourCornerMachineInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}
