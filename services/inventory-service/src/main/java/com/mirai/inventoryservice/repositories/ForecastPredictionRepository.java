package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForecastPredictionRepository extends JpaRepository<ForecastPrediction, UUID> {
    // Find latest prediction for a specific inventory item
    Optional<ForecastPrediction> findFirstByItemIdOrderByComputedAtDesc(UUID itemId);

    // Find all predictions for a specific inventory item
    List<ForecastPrediction> findByItemIdOrderByComputedAtDesc(UUID itemId);

    // Find only the latest prediction per item (paginated)
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at",
            countQuery = "SELECT COUNT(DISTINCT item_id) FROM forecast_predictions",
            nativeQuery = true)
    Page<ForecastPrediction> findLatestPerItem(Pageable pageable);

    // Find only the latest prediction per item (non-paginated, for analytics)
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at",
            nativeQuery = true)
    List<ForecastPrediction> findAllLatest();

    // Find latest predictions limited to N items, ordered by urgency (days to stockout)
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "ORDER BY fp.days_to_stockout ASC NULLS LAST "
            + "LIMIT :limit",
            nativeQuery = true)
    List<ForecastPrediction> findAllLatestLimited(@Param("limit") int limit);

    // Find at-risk items using only the latest prediction per item
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "WHERE fp.days_to_stockout < :threshold "
            + "ORDER BY fp.days_to_stockout ASC",
            nativeQuery = true)
    List<ForecastPrediction> findLatestAtRisk(@Param("threshold") double threshold);

    // Delete all predictions for a specific inventory item
    void deleteByItemId(UUID itemId);

    // Delete predictions with suggested order date before the given date (overdue cleanup)
    int deleteBySuggestedOrderDateBefore(LocalDate date);

    // Find the item with highest demand (lowest avgDailyDelta) using only the latest prediction per item
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "WHERE fp.avg_daily_delta < 0 "
            + "ORDER BY fp.avg_daily_delta ASC "
            + "LIMIT 1",
            nativeQuery = true)
    Optional<ForecastPrediction> findHighestDemandForecast();
}

