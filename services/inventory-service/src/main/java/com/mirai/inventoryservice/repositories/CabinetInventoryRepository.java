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
    @Query("SELECT i FROM CabinetInventory i JOIN FETCH i.cabinet JOIN FETCH i.item WHERE i.cabinet.id = :cabinetId")
    List<CabinetInventory> findByCabinet_Id(@Param("cabinetId") UUID cabinetId);

    @Query("SELECT i FROM CabinetInventory i JOIN FETCH i.cabinet WHERE i.item.id = :productId")
    List<CabinetInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM CabinetInventory i JOIN FETCH i.cabinet JOIN FETCH i.item WHERE i.cabinet.id = :cabinetId AND i.item.id = :itemId")
    Optional<CabinetInventory> findByCabinet_IdAndItem_Id(@Param("cabinetId") UUID cabinetId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM CabinetInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

