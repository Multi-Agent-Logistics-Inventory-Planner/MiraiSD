package com.mirai.inventoryservice.sites.infrastructure;

import com.mirai.inventoryservice.sites.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteRepository extends JpaRepository<Site, UUID> {
    Optional<Site> findByCode(String code);
    boolean existsByCode(String code);
}
