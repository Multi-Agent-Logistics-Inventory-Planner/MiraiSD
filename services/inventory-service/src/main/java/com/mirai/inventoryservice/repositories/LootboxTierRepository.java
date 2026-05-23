package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.LootboxTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LootboxTierRepository extends JpaRepository<LootboxTier, UUID> {

    Optional<LootboxTier> findByLootboxIdAndName(UUID lootboxId, String name);

    List<LootboxTier> findByLootboxIdOrderBySortOrderAscNameAsc(UUID lootboxId);

    /**
     * Batch lookup for the admin "list crates" path so a single query loads tiers across
     * many crates instead of one-per-crate.
     */
    List<LootboxTier> findByLootboxIdInOrderBySortOrderAscNameAsc(Collection<UUID> lootboxIds);

    List<LootboxTier> findAllByOrderBySortOrderAscNameAsc();

    /**
     * Active tiers under a given crate that have at least one active prize — these
     * participate in the roll.
     */
    @Query("""
            SELECT DISTINCT t FROM LootboxTier t
            JOIN LootboxPrize p ON p.tier = t
            WHERE t.lootbox.id = :lootboxId
              AND t.active = true
              AND p.active = true
            ORDER BY t.sortOrder ASC, t.name ASC
            """)
    List<LootboxTier> findRollableTiersByLootbox(@Param("lootboxId") UUID lootboxId);
}
