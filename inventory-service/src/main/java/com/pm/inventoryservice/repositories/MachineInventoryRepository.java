package com.pm.inventoryservice.repositories;

import com.pm.inventoryservice.models.MachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MachineInventoryRepository extends JpaRepository<MachineInventory, UUID> {
    List<MachineInventory> findByMachineId(UUID machineId);
    Optional<MachineInventory> findByIdAndMachineId(UUID id, UUID machineId);
}
