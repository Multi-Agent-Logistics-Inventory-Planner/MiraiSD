package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.analytics.DailySalesRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailySalesRollupRepository extends JpaRepository<DailySalesRollup, UUID> {

    // Find rollup for specific item and date
    Optional<DailySalesRollup> findByItemIdAndRollupDate(UUID itemId, LocalDate rollupDate);

    // Find all rollups for an item within date range
    List<DailySalesRollup> findByItemIdAndRollupDateBetweenOrderByRollupDateAsc(
        UUID itemId, LocalDate startDate, LocalDate endDate);

    // Find all rollups within date range (for category aggregation)
    List<DailySalesRollup> findByRollupDateBetweenOrderByRollupDateAsc(
        LocalDate startDate, LocalDate endDate);

    // Aggregate daily sales by day-of-week for insights (native query for EXTRACT)
    @Query(value = """
        SELECT EXTRACT(DOW FROM r.rollup_date) as dow,
               SUM(r.units_sold) as total_units,
               SUM(r.revenue) as total_revenue
        FROM analytics_daily_rollup r
        WHERE r.rollup_date >= :startDate
        GROUP BY EXTRACT(DOW FROM r.rollup_date)
        ORDER BY dow
        """, nativeQuery = true)
    List<Object[]> aggregateByDayOfWeek(@Param("startDate") LocalDate startDate);

    // Top selling items by units sold in date range
    @Query("""
        SELECT r.itemId, SUM(r.unitsSold) as totalUnits, SUM(r.revenue) as totalRevenue
        FROM DailySalesRollup r
        WHERE r.rollupDate >= :startDate AND r.rollupDate <= :endDate
        GROUP BY r.itemId
        ORDER BY totalUnits DESC
        """)
    List<Object[]> findTopSellersByUnits(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    // Top selling items by revenue in date range
    @Query("""
        SELECT r.itemId, SUM(r.unitsSold) as totalUnits, SUM(r.revenue) as totalRevenue
        FROM DailySalesRollup r
        WHERE r.rollupDate >= :startDate AND r.rollupDate <= :endDate
        GROUP BY r.itemId
        ORDER BY totalRevenue DESC
        """)
    List<Object[]> findTopSellersByRevenue(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    // Total revenue and units for a date range
    @Query("""
        SELECT COALESCE(SUM(r.unitsSold), 0), COALESCE(SUM(r.revenue), 0)
        FROM DailySalesRollup r
        WHERE r.rollupDate >= :startDate AND r.rollupDate <= :endDate
        """)
    Object[] getTotalsForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    // Monthly aggregation from daily rollups (native query for EXTRACT)
    @Query(value = """
        SELECT EXTRACT(YEAR FROM r.rollup_date) as year,
               EXTRACT(MONTH FROM r.rollup_date) as month,
               SUM(r.units_sold) as total_units,
               SUM(r.revenue) as total_revenue,
               COUNT(DISTINCT r.item_id) as unique_items
        FROM analytics_daily_rollup r
        WHERE r.rollup_date >= :startDate
        GROUP BY EXTRACT(YEAR FROM r.rollup_date), EXTRACT(MONTH FROM r.rollup_date)
        ORDER BY year DESC, month DESC
        """, nativeQuery = true)
    List<Object[]> aggregateByMonth(@Param("startDate") LocalDate startDate);

    // Delete rollups older than retention period
    @Modifying
    @Query("DELETE FROM DailySalesRollup r WHERE r.rollupDate < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDate cutoffDate);

    // Find rollups for seeded data cleanup
    @Query("""
        SELECT r FROM DailySalesRollup r
        WHERE r.computedAt >= :since
        ORDER BY r.computedAt DESC
        """)
    List<DailySalesRollup> findRecentlyComputed(@Param("since") java.time.OffsetDateTime since);
}
