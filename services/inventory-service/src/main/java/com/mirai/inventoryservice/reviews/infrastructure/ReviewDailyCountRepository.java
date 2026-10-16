package com.mirai.inventoryservice.reviews.infrastructure;

import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.reviews.domain.ReviewDailyCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewDailyCountRepository extends JpaRepository<ReviewDailyCount, UUID> {

    List<ReviewDailyCount> findByDateOrderByReviewCountDesc(LocalDate date);

    List<ReviewDailyCount> findByUserAndDateBetweenOrderByDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate);

    @Query("SELECT c.user.id, c.user.fullName, SUM(c.reviewCount), AVG(c.reviewCount) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.siteId = :siteId AND c.user IS NOT NULL AND c.date >= :startDate AND c.date <= :endDate " +
            "GROUP BY c.user.id, c.user.fullName " +
            "ORDER BY SUM(c.reviewCount) DESC")
    List<Object[]> getMonthlySummariesByUserAndSite(
            @Param("siteId") UUID siteId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // All-time stats for a specific user at a site: [totalReviews, firstDate, lastDate]
    @Query("SELECT SUM(c.reviewCount), MIN(c.date), MAX(c.date) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.user.id = :userId AND c.siteId = :siteId")
    List<Object[]> getAllTimeStatsByUserAndSite(@Param("userId") UUID userId, @Param("siteId") UUID siteId);

    /**
     * Combined lifetime + expired review-credit sums in one round-trip, for
     * `LootboxService.computeBalance`. Returns a single-row list of
     * `[totalAwarded: Long, expiredAwarded: Long]`. Wrapped in `List<Object[]>` to match
     * how Hibernate hands back multi-column aggregate projections in this codebase
     * (see `getAllTimeStatsByUser`).
     * Sums coins_awarded (immutable per row) so rate changes never re-price history.
     */
    @Query("SELECT COALESCE(SUM(c.coinsAwarded), 0), " +
            "COALESCE(SUM(CASE WHEN c.expiresAt <= :today THEN c.coinsAwarded ELSE 0 END), 0) " +
            "FROM ReviewDailyCount c " +
            "WHERE c.user.id = :userId")
    List<Object[]> sumCoinTotalsByUserId(@Param("userId") UUID userId,
                                         @Param("today") LocalDate today);

    /**
     * Daily review credit rows for the user's history view (newest first), every row
     * included. Returns [date, reviewCount, coinsAwarded, expiresAt] so the UI can
     * label the row with both the review count and the coins they earned.
     */
    @Query("SELECT c.date, c.reviewCount, c.coinsAwarded, c.expiresAt " +
            "FROM ReviewDailyCount c " +
            "WHERE c.user.id = :userId AND c.siteId = :siteId " +
            "ORDER BY c.date DESC")
    List<Object[]> findDailyCreditsByUser(@Param("userId") UUID userId, @Param("siteId") UUID siteId);

    /** Rows whose expires_at is between (today, today + windowDays] for the "expiring soon" UI. */
    @Query("SELECT c FROM ReviewDailyCount c " +
            "WHERE c.user.id = :userId AND c.siteId = :siteId " +
            "  AND c.expiresAt > :today AND c.expiresAt <= :until " +
            "ORDER BY c.expiresAt ASC")
    List<ReviewDailyCount> findExpiringSoon(@Param("userId") UUID userId,
                                            @Param("siteId") UUID siteId,
                                            @Param("today") LocalDate today,
                                            @Param("until") LocalDate until);

    /** This user's all-time rank (1-based) within one site without loading all users' totals. */
    @Query(value = "SELECT 1 + COALESCE(COUNT(*), 0) FROM (" +
            "SELECT c.user_id, SUM(c.review_count) AS total FROM review_daily_counts c " +
            "WHERE c.site_id = :siteId AND c.user_id IS NOT NULL GROUP BY c.user_id " +
            "HAVING SUM(c.review_count) > (" +
            "SELECT COALESCE(SUM(c2.review_count), 0) FROM review_daily_counts c2 " +
            "WHERE c2.user_id = :userId AND c2.site_id = :siteId" +
            ")) t", nativeQuery = true)
    long getAllTimeRankByUserAndSite(@Param("userId") UUID userId, @Param("siteId") UUID siteId);
}
