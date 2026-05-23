package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.LootboxPrize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface LootboxPrizeRepository extends JpaRepository<LootboxPrize, UUID> {

    @Query("SELECT p FROM LootboxPrize p WHERE p.tier.id = :tierId AND p.active = true")
    List<LootboxPrize> findActiveByTierId(@Param("tierId") UUID tierId);

    @Query("SELECT COUNT(p) FROM LootboxPrize p WHERE p.tier.id = :tierId AND p.active = true")
    long countActiveByTierId(@Param("tierId") UUID tierId);

    /**
     * Batched active-prize count per tier — pairs with `findByLootboxIdIn...` to populate
     * the admin "list crates" view without one COUNT query per tier. Returns rows of
     * `[tierId: UUID, count: Long]`; tiers with zero active prizes are absent.
     */
    @Query("SELECT p.tier.id, COUNT(p) FROM LootboxPrize p " +
            "WHERE p.tier.id IN :tierIds AND p.active = true " +
            "GROUP BY p.tier.id")
    List<Object[]> countActiveGroupedByTierIds(@Param("tierIds") Collection<UUID> tierIds);

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

    /**
     * Player-facing catalog query: returns active prizes AND depleted (quantity = 0)
     * prizes so the UI can render a "SOLD OUT" card alongside still-rollable ones.
     * Manually-deactivated prizes (active = false, quantity != 0) stay hidden — only
     * stock-depletion surfaces the sold-out state.
     */
    @Query("""
            SELECT p FROM LootboxPrize p
            JOIN FETCH p.tier t
            WHERE p.active = true OR p.quantity = 0
            ORDER BY t.sortOrder ASC, p.name ASC
            """)
    List<LootboxPrize> findAllActiveOrSoldOutWithTier();

    /**
     * Atomic stock decrement. Returns affected-row count: 1 on success, 0 if the prize
     * is already depleted or inactive (caller should re-roll). The same UPDATE flips
     * active = false when the last copy is taken, so subsequent rolls skip it via
     * findActiveByTierId.
     */
    @Modifying
    @Query(value = """
            UPDATE lootbox_prizes
               SET quantity = quantity - 1,
                   active   = CASE WHEN quantity - 1 = 0 THEN false ELSE active END,
                   updated_at = NOW()
             WHERE id = :id
               AND active = true
               AND quantity IS NOT NULL
               AND quantity > 0
            """, nativeQuery = true)
    int decrementQuantity(@Param("id") UUID id);
}
