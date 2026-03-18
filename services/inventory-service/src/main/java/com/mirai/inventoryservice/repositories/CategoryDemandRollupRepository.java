package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.analytics.CategoryDemandRollup;
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
public interface CategoryDemandRollupRepository extends JpaRepository<CategoryDemandRollup, UUID> {

    /**
     * Find rollup for specific category and date.
     */
    Optional<CategoryDemandRollup> findByCategoryIdAndRollupDate(UUID categoryId, LocalDate rollupDate);

    /**
     * Find all rollups for a category within date range.
     */
    List<CategoryDemandRollup> findByCategoryIdAndRollupDateBetweenOrderByRollupDateAsc(
        UUID categoryId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all rollups for a specific date.
     */
    List<CategoryDemandRollup> findByRollupDateOrderByCategoryIdAsc(LocalDate rollupDate);

    /**
     * Find all rollups within date range.
     */
    List<CategoryDemandRollup> findByRollupDateBetweenOrderByRollupDateAsc(
        LocalDate startDate, LocalDate endDate);

    /**
     * Find latest rollup for each category.
     */
    @Query(value = """
        SELECT cr.* FROM analytics_category_demand_rollup cr
        INNER JOIN (
            SELECT category_id, MAX(rollup_date) AS max_date
            FROM analytics_category_demand_rollup
            GROUP BY category_id
        ) latest ON cr.category_id = latest.category_id AND cr.rollup_date = latest.max_date
        ORDER BY cr.total_demand_velocity DESC
        """, nativeQuery = true)
    List<CategoryDemandRollup> findAllLatest();

    /**
     * Get total demand velocity across all categories for a date.
     */
    @Query("""
        SELECT COALESCE(SUM(cr.totalDemandVelocity), 0) FROM CategoryDemandRollup cr
        WHERE cr.rollupDate = :date
        """)
    Double getTotalDemandVelocityForDate(@Param("date") LocalDate date);

    /**
     * Get categories ranked by demand velocity for a date.
     */
    @Query("""
        SELECT cr FROM CategoryDemandRollup cr
        WHERE cr.rollupDate = :date
        ORDER BY cr.totalDemandVelocity DESC
        """)
    List<CategoryDemandRollup> findByDateOrderByDemandDesc(@Param("date") LocalDate date);

    /**
     * Delete rollups older than retention period.
     */
    @Modifying
    @Query("DELETE FROM CategoryDemandRollup cr WHERE cr.rollupDate < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Check if rollup exists for category on date.
     */
    boolean existsByCategoryIdAndRollupDate(UUID categoryId, LocalDate rollupDate);
}
