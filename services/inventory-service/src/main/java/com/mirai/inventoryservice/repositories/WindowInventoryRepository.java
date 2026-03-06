package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.WindowInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WindowInventoryRepository extends JpaRepository<WindowInventory, UUID> {
    @Query("SELECT i FROM WindowInventory i JOIN FETCH i.window JOIN FETCH i.item WHERE i.window.id = :windowId")
    List<WindowInventory> findByWindow_Id(@Param("windowId") UUID windowId);

    @Query("SELECT i FROM WindowInventory i JOIN FETCH i.window WHERE i.item.id = :productId")
    List<WindowInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM WindowInventory i JOIN FETCH i.window JOIN FETCH i.item WHERE i.window.id = :windowId AND i.item.id = :itemId")
    Optional<WindowInventory> findByWindow_IdAndItem_Id(@Param("windowId") UUID windowId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM WindowInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);

    void deleteByItem_Id(UUID productId);
}

