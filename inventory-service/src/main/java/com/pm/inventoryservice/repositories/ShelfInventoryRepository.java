package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.ShelfInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShelfInventoryRepository extends JpaRepository<ShelfInventory, UUID> {
    List<ShelfInventory> findByShelfId(UUID shelfId);
    Optional<ShelfInventory> findByIdAndShelfId(UUID id, UUID shelfId);
}

