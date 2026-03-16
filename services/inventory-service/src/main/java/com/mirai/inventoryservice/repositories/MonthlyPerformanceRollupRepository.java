package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.analytics.MonthlyPerformanceRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlyPerformanceRollupRepository extends JpaRepository<MonthlyPerformanceRollup, UUID> {

    // Find rollup for specific category and month
    Optional<MonthlyPerformanceRollup> findByCategoryIdAndRollupYearAndRollupMonth(
        UUID categoryId, Integer year, Integer month);

    // Find all rollups for a category ordered by time
    List<MonthlyPerformanceRollup> findByCategoryIdOrderByRollupYearDescRollupMonthDesc(UUID categoryId);

    // Find all rollups for a specific month (all categories)
    List<MonthlyPerformanceRollup> findByRollupYearAndRollupMonthOrderByCategoryId(
        Integer year, Integer month);

    // Find rollups within year range for trend analysis
    @Query("""
        SELECT r FROM MonthlyPerformanceRollup r
        WHERE r.rollupYear >= :startYear
        ORDER BY r.rollupYear DESC, r.rollupMonth DESC, r.categoryId
        """)
    List<MonthlyPerformanceRollup> findByYearOnwards(@Param("startYear") Integer startYear);

    // Category performance comparison for specific months
    @Query("""
        SELECT r FROM MonthlyPerformanceRollup r
        WHERE (r.rollupYear = :year AND r.rollupMonth >= :startMonth)
           OR (r.rollupYear = :year AND r.rollupMonth <= :endMonth)
        ORDER BY r.categoryId, r.rollupYear DESC, r.rollupMonth DESC
        """)
    List<MonthlyPerformanceRollup> findByYearAndMonthRange(
        @Param("year") Integer year,
        @Param("startMonth") Integer startMonth,
        @Param("endMonth") Integer endMonth);

    // Top performing categories by revenue for a specific month
    @Query("""
        SELECT r FROM MonthlyPerformanceRollup r
        WHERE r.rollupYear = :year AND r.rollupMonth = :month
        ORDER BY r.totalRevenue DESC
        """)
    List<MonthlyPerformanceRollup> findTopCategoriesByRevenue(
        @Param("year") Integer year,
        @Param("month") Integer month);

    // Aggregate totals across all categories for a month
    @Query("""
        SELECT SUM(r.totalUnitsSold), SUM(r.totalRevenue), SUM(r.uniqueItemsSold)
        FROM MonthlyPerformanceRollup r
        WHERE r.rollupYear = :year AND r.rollupMonth = :month
        """)
    Object[] getMonthlyTotals(@Param("year") Integer year, @Param("month") Integer month);

    // Year-over-year comparison
    @Query("""
        SELECT r.categoryId,
               r.rollupYear,
               SUM(r.totalRevenue) as yearlyRevenue,
               SUM(r.totalUnitsSold) as yearlyUnits
        FROM MonthlyPerformanceRollup r
        WHERE r.rollupYear IN (:year1, :year2)
        GROUP BY r.categoryId, r.rollupYear
        ORDER BY r.categoryId, r.rollupYear DESC
        """)
    List<Object[]> compareYears(@Param("year1") Integer year1, @Param("year2") Integer year2);

    // Delete old rollups for retention
    @Modifying
    @Query("""
        DELETE FROM MonthlyPerformanceRollup r
        WHERE r.rollupYear < :cutoffYear
           OR (r.rollupYear = :cutoffYear AND r.rollupMonth < :cutoffMonth)
        """)
    int deleteOlderThan(@Param("cutoffYear") Integer cutoffYear, @Param("cutoffMonth") Integer cutoffMonth);

    // Find most recent rollups for each category
    @Query("""
        SELECT r FROM MonthlyPerformanceRollup r
        WHERE r.rollupYear = :year AND r.rollupMonth = :month
        """)
    List<MonthlyPerformanceRollup> findLatestForAllCategories(
        @Param("year") Integer year,
        @Param("month") Integer month);
}
