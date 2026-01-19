package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.inventory.RackInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RackInventoryRepository extends JpaRepository<RackInventory, UUID> {
    List<RackInventory> findByRack_Id(UUID rackId);
}

