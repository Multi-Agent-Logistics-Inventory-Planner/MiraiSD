package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.storage.Gachapon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GachaponRepository extends JpaRepository<Gachapon, UUID> {
    Optional<Gachapon> findByGachaponCode(String gachaponCode);
    boolean existsByGachaponCode(String gachaponCode);
}
