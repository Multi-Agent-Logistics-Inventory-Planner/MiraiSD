package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.Cabinet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CabinetRepository extends JpaRepository<Cabinet, UUID> {
    Optional<Cabinet> findByCabinetCode(String cabinetCode);
    boolean existsByCabinetCode(String cabinetCode);
}

