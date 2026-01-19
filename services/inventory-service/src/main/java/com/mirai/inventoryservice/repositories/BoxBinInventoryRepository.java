package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.BoxBinInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BoxBinInventoryRepository extends JpaRepository<BoxBinInventory, UUID> {
    List<BoxBinInventory> findByBoxBin_Id(UUID boxBinId);
}

