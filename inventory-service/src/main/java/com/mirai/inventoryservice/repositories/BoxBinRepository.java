package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.BoxBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoxBinRepository extends JpaRepository<BoxBin, UUID> {
    Optional<BoxBin> findByBoxBinCode(String boxBinCode);
    boolean existsByBoxBinCode(String boxBinCode);
}

