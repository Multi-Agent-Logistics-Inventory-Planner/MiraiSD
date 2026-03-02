package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoxBinInventoryRepository extends JpaRepository<BoxBinInventory, UUID> {
    @Query("SELECT i FROM BoxBinInventory i JOIN FETCH i.boxBin JOIN FETCH i.item WHERE i.boxBin.id = :boxBinId")
    List<BoxBinInventory> findByBoxBin_Id(@Param("boxBinId") UUID boxBinId);

    @Query("SELECT i FROM BoxBinInventory i JOIN FETCH i.boxBin WHERE i.item.id = :productId")
    List<BoxBinInventory> findByItem_Id(@Param("productId") UUID productId);

    @Query("SELECT i FROM BoxBinInventory i JOIN FETCH i.boxBin JOIN FETCH i.item WHERE i.boxBin.id = :boxBinId AND i.item.id = :itemId")
    Optional<BoxBinInventory> findByBoxBin_IdAndItem_Id(@Param("boxBinId") UUID boxBinId, @Param("itemId") UUID itemId);

    @Query("SELECT SUM(i.quantity) FROM BoxBinInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

