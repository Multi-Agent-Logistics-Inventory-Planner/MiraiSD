package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.CabinetInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CabinetInventoryRepository extends JpaRepository<CabinetInventory, UUID> {
    List<CabinetInventory> findByCabinet_Id(UUID cabinetId);

    List<CabinetInventory> findByItem_Id(UUID productId);

    Optional<CabinetInventory> findByCabinet_IdAndItem_Id(UUID cabinetId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM CabinetInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

