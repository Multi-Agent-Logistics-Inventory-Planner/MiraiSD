package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LootboxTierRepository extends JpaRepository<LootboxTier, UUID> {

    Optional<LootboxTier> findByName(String name);

    List<LootboxTier> findAllByOrderBySortOrderAscNameAsc();

    /** Active tiers that have at least one active prize — these participate in the roll. */
    @Query("""
            SELECT DISTINCT t FROM LootboxTier t
            JOIN LootboxPrize p ON p.tier = t
            WHERE t.active = true AND p.active = true
            ORDER BY t.sortOrder ASC, t.name ASC
            """)
    List<LootboxTier> findRollableTiers();
}
