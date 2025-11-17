package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.BinInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BinInventoryRepository extends JpaRepository<BinInventory, UUID> {
    List<BinInventory> findByBinId(UUID binId);
    Optional<BinInventory> findByIdAndBinId(UUID id, UUID binId);
}

