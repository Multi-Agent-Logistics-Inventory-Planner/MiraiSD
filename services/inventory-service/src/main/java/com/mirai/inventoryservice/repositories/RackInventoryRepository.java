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
    @Query("SELECT i FROM RackInventory i JOIN FETCH i.rack JOIN FETCH i.item WHERE i.rack.id = :rackId")
    List<RackInventory> findByRack_Id(@Param("rackId") UUID rackId);

    @Query("SELECT i FROM RackInventory i JOIN FETCH i.rack WHERE i.item.id = :productId")
    List<RackInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM RackInventory i JOIN FETCH i.rack JOIN FETCH i.item WHERE i.rack.id = :rackId AND i.item.id = :itemId")
    Optional<RackInventory> findByRack_IdAndItem_Id(@Param("rackId") UUID rackId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM RackInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);

    void deleteByItem_Id(UUID productId);
}

