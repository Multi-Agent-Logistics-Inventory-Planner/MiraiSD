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
    List<BoxBinInventory> findByBoxBin_Id(UUID boxBinId);

    List<BoxBinInventory> findByItem_Id(UUID productId);

    Optional<BoxBinInventory> findByBoxBin_IdAndItem_Id(UUID boxBinId, UUID productId);

    @Query("SELECT SUM(i.quantity) FROM BoxBinInventory i WHERE i.item.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") UUID productId);
}

