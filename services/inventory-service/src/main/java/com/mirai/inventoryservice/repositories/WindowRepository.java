package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WindowRepository extends JpaRepository<Window, UUID> {
    Optional<Window> findByWindowCode(String windowCode);
    boolean existsByWindowCode(String windowCode);
}

