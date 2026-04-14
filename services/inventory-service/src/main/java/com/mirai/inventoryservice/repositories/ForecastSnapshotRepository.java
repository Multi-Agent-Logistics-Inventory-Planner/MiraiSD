package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.analytics.ForecastDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForecastSnapshotRepository extends JpaRepository<ForecastDailySnapshot, UUID> {

    /**
     * Find snapshot for specific item and date.
     */
    Optional<ForecastDailySnapshot> findByItemIdAndSnapshotDate(UUID itemId, LocalDate snapshotDate);

    /**
     * Find all snapshots for an item within date range.
     */
    List<ForecastDailySnapshot> findByItemIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
        UUID itemId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all snapshots for a specific date (for category aggregation).
     */
    List<ForecastDailySnapshot> findBySnapshotDate(LocalDate snapshotDate);

    /**
     * Find all snapshots within date range.
     */
    List<ForecastDailySnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(
        LocalDate startDate, LocalDate endDate);

    /**
     * Find latest snapshot for each item.
     */
    @Query(value = """
        SELECT fs.* FROM analytics_forecast_snapshot fs
        INNER JOIN (
            SELECT item_id, MAX(snapshot_date) AS max_date
            FROM analytics_forecast_snapshot
            GROUP BY item_id
        ) latest ON fs.item_id = latest.item_id AND fs.snapshot_date = latest.max_date
        """, nativeQuery = true)
    List<ForecastDailySnapshot> findAllLatest();

    /**
     * Calculate average demand velocity for date range.
     */
    @Query("""
        SELECT AVG(fs.muHat) FROM ForecastDailySnapshot fs
        WHERE fs.snapshotDate >= :startDate AND fs.snapshotDate <= :endDate
        """)
    Optional<Double> getAverageDemandVelocity(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * Delete snapshots older than retention period.
     */
    @Modifying
    @Query("DELETE FROM ForecastDailySnapshot fs WHERE fs.snapshotDate < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Check if snapshot exists for item on date.
     */
    boolean existsByItemIdAndSnapshotDate(UUID itemId, LocalDate snapshotDate);

    /**
     * Find all item IDs that have snapshots for a given date.
     * Used to batch-check existence instead of N individual queries.
     */
    @Query("SELECT fs.itemId FROM ForecastDailySnapshot fs WHERE fs.snapshotDate = :date")
    List<UUID> findItemIdsBySnapshotDate(@Param("date") LocalDate date);
}
