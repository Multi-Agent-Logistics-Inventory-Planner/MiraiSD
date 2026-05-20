package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LootboxPrizeRepository extends JpaRepository<LootboxPrize, UUID> {

    @Query("SELECT p FROM LootboxPrize p WHERE p.tier.id = :tierId AND p.active = true")
    List<LootboxPrize> findActiveByTierId(@Param("tierId") UUID tierId);

    @Query("SELECT COUNT(p) FROM LootboxPrize p WHERE p.tier.id = :tierId AND p.active = true")
    long countActiveByTierId(@Param("tierId") UUID tierId);

    @Query("""
            SELECT p FROM LootboxPrize p
            JOIN FETCH p.tier t
            WHERE p.active = true
            ORDER BY t.sortOrder ASC, p.name ASC
            """)
    List<LootboxPrize> findAllActiveWithTier();

    @Query("""
            SELECT p FROM LootboxPrize p
            JOIN FETCH p.tier t
            ORDER BY t.sortOrder ASC, p.name ASC
            """)
    List<LootboxPrize> findAllWithTier();
}
