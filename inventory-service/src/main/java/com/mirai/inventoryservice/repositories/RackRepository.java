package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.Rack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RackRepository extends JpaRepository<Rack, UUID> {
    Optional<Rack> findByRackCode(String rackCode);
    boolean existsByRackCode(String rackCode);
}

