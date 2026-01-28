package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.NotAssignedInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotAssignedInventoryRepository extends JpaRepository<NotAssignedInventory, UUID> {
    List<NotAssignedInventory> findAllByItem_Id(UUID productId);

    Optional<NotAssignedInventory> findByItem_Id(UUID productId);

    @Query("SELECT SUM(i.quantity) FROM NotAssignedInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}
