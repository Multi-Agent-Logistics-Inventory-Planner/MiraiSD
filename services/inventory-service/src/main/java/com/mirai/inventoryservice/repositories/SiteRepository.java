package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteRepository extends JpaRepository<Site, UUID> {
    Optional<Site> findByCode(String code);
    boolean existsByCode(String code);
}
