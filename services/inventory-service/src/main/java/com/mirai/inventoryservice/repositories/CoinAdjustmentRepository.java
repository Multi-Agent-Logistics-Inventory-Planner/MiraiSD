package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CoinAdjustmentRepository extends JpaRepository<CoinAdjustment, UUID> {

    /**
     * Combined lifetime + expired adjustment sums in one round-trip, for
     * `LootboxService.computeBalance`. Returns a single-row list of
     * `[totalDelta: Long, expiredDelta: Long]`.
     */
    @Query("SELECT COALESCE(SUM(a.delta), 0), " +
            "COALESCE(SUM(CASE WHEN a.expiresAt <= :now THEN a.delta ELSE 0 END), 0) " +
            "FROM CoinAdjustment a WHERE a.user.id = :userId")
    List<Object[]> sumAdjustmentTotalsByUserId(@Param("userId") UUID userId,
                                               @Param("now") OffsetDateTime now);

    /** Rows whose expires_at is between (now, now + window] — for the "expiring soon" UI. */
    @Query("SELECT a FROM CoinAdjustment a " +
            "WHERE a.user.id = :userId " +
            "  AND a.expiresAt > :now AND a.expiresAt <= :until " +
            "ORDER BY a.expiresAt ASC")
    List<CoinAdjustment> findExpiringSoon(@Param("userId") UUID userId,
                                          @Param("now") OffsetDateTime now,
                                          @Param("until") OffsetDateTime until);

    @EntityGraph(attributePaths = {"user", "grantedBy"})
    List<CoinAdjustment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
