package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.RackInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RackInventoryRepository extends JpaRepository<RackInventory, UUID> {
    List<RackInventory> findByRack_Id(UUID rackId);

    List<RackInventory> findByItem_Id(UUID productId);

    Optional<RackInventory> findByRack_IdAndItem_Id(UUID rackId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM RackInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

