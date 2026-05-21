package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.lootbox.CoinAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CoinAdjustmentRepository extends JpaRepository<CoinAdjustment, UUID> {

    /** Lifetime sum of all adjustments for a user (regardless of expiry). */
    @Query("SELECT COALESCE(SUM(a.delta), 0) FROM CoinAdjustment a WHERE a.user.id = :userId")
    long sumDeltaByUserId(@Param("userId") UUID userId);

    /** Sum of adjustments whose expires_at is at or before `now` — the "expired" pool. */
    @Query("SELECT COALESCE(SUM(a.delta), 0) FROM CoinAdjustment a " +
            "WHERE a.user.id = :userId AND a.expiresAt <= :now")
    long sumExpiredDeltaByUserId(@Param("userId") UUID userId,
                                 @Param("now") OffsetDateTime now);

    /** Rows whose expires_at is between (now, now + window] — for the "expiring soon" UI. */
    @Query("SELECT a FROM CoinAdjustment a " +
            "WHERE a.user.id = :userId " +
            "  AND a.expiresAt > :now AND a.expiresAt <= :until " +
            "ORDER BY a.expiresAt ASC")
    List<CoinAdjustment> findExpiringSoon(@Param("userId") UUID userId,
                                          @Param("now") OffsetDateTime now,
                                          @Param("until") OffsetDateTime until);

    List<CoinAdjustment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
