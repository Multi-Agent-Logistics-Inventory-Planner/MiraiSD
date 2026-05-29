package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForecastPredictionRepository extends JpaRepository<ForecastPrediction, UUID> {
    // Find latest prediction for a specific inventory item
    Optional<ForecastPrediction> findFirstByItemIdOrderByComputedAtDesc(UUID itemId);

    // Find all predictions for a specific inventory item
    List<ForecastPrediction> findByItemIdOrderByComputedAtDesc(UUID itemId);

    // Column list explicitly excluding the heavy `features` JSONB column for forecast-only
    // callers (ForecastService + ProductReportBundleService never read features).
    // We project `NULL AS features` so Hibernate's entity result-set mapper still finds
    // a column by that name and hydrates `features = null` instead of erroring out.
    // Methods that DO need features (AnalyticsService.extractFeatures, AnalyticsSeedService,
    // DevSeedController) continue to use findAllLatest below, which still SELECTs fp.*.
    String FORECAST_COLS_NO_FEATURES =
            "fp.id, fp.item_id, fp.horizon_days, fp.avg_daily_delta, fp.days_to_stockout, "
                    + "fp.suggested_reorder_qty, fp.suggested_order_date, fp.confidence, "
                    + "fp.computed_at, NULL AS features";

    // Find only the latest prediction per item (paginated). `features` is null on returned entities.
    @Query(value = "SELECT " + FORECAST_COLS_NO_FEATURES + " FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at",
            countQuery = "SELECT COUNT(DISTINCT item_id) FROM forecast_predictions",
            nativeQuery = true)
    Page<ForecastPrediction> findLatestPerItem(Pageable pageable);

    // Find only the latest prediction per item (non-paginated). Keeps fp.* because
    // AnalyticsService callers consume `features` here.
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at",
            nativeQuery = true)
    List<ForecastPrediction> findAllLatest();

    // Find latest predictions limited to N items, ordered by urgency (days to stockout).
    // `features` is null on returned entities.
    @Query(value = "SELECT " + FORECAST_COLS_NO_FEATURES + " FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "ORDER BY fp.days_to_stockout ASC NULLS LAST "
            + "LIMIT :limit",
            nativeQuery = true)
    List<ForecastPrediction> findAllLatestLimited(@Param("limit") int limit);

    // Find latest prediction per item, filtered to a set of item IDs. `features` is null.
    @Query(value = "SELECT " + FORECAST_COLS_NO_FEATURES + " FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "WHERE fp.item_id IN :itemIds",
            nativeQuery = true)
    List<ForecastPrediction> findLatestByItemIds(@Param("itemIds") List<UUID> itemIds);

    // Find at-risk items using only the latest prediction per item. `features` is null.
    @Query(value = "SELECT " + FORECAST_COLS_NO_FEATURES + " FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "WHERE fp.days_to_stockout < :threshold "
            + "ORDER BY fp.days_to_stockout ASC",
            nativeQuery = true)
    List<ForecastPrediction> findLatestAtRisk(@Param("threshold") double threshold);

    // Delete all predictions for a specific inventory item
    void deleteByItemId(UUID itemId);

    // Batch delete all predictions for multiple inventory items (optimized for N+1 prevention)
    @Modifying
    @Query("DELETE FROM ForecastPrediction fp WHERE fp.itemId IN :itemIds")
    void deleteAllByItemIdIn(@Param("itemIds") Collection<UUID> itemIds);

    // Delete predictions with suggested order date before the given date (overdue cleanup)
    int deleteBySuggestedOrderDateBefore(LocalDate date);

    // Find the item with highest demand (lowest avgDailyDelta) using only the latest prediction per item.
    // `features` is null on the returned entity.
    @Query(value = "SELECT " + FORECAST_COLS_NO_FEATURES + " FROM forecast_predictions fp "
            + "INNER JOIN (SELECT item_id, MAX(computed_at) AS max_computed_at "
            + "FROM forecast_predictions GROUP BY item_id) latest "
            + "ON fp.item_id = latest.item_id AND fp.computed_at = latest.max_computed_at "
            + "WHERE fp.avg_daily_delta < 0 "
            + "ORDER BY fp.avg_daily_delta ASC "
            + "LIMIT 1",
            nativeQuery = true)
    Optional<ForecastPrediction> findHighestDemandForecast();

    /**
     * Accuracy aggregates by category over a rolling window. For each (item, actual_day) in
     * the window, takes the latest forecast_predictions row strictly before that day, joins
     * mu_hat against analytics_daily_rollup.units_sold, then groups by category.
     *
     * Returns rows of:
     *   [0] category          (String, "(uncategorized)" when null)
     *   [1] scored_item_days  (Long)
     *   [2] sum_abs_error     (BigDecimal)   numerator of WAPE
     *   [3] sum_actual        (Long)         denominator of WAPE
     *   [4] sum_signed_error  (BigDecimal)   for bias
     *   [5] mape_sale_days    (BigDecimal)   AVG over actual > 0 only (nullable)
     *   [6] under_count       (Long)         predicted < actual
     *   [7] over_count        (Long)         predicted > actual
     */
    @Query(value = """
        WITH actuals AS (
          SELECT item_id, rollup_date, units_sold
          FROM analytics_daily_rollup
          WHERE rollup_date >= :startDate
            AND rollup_date <= :endDate
            AND units_sold IS NOT NULL
        ),
        preds AS (
          SELECT
            a.item_id,
            a.rollup_date,
            a.units_sold,
            (SELECT (fp.features->>'mu_hat')::numeric
                  * COALESCE(
                      (fp.features->'dow_multipliers'->>((EXTRACT(isodow FROM a.rollup_date)::int - 1)::text))::numeric,
                      1.0
                    )
             FROM forecast_predictions fp
             WHERE fp.item_id = a.item_id
               AND fp.computed_at < (a.rollup_date::timestamptz)
               AND fp.features->>'mu_hat' IS NOT NULL
             ORDER BY fp.computed_at DESC
             LIMIT 1) AS predicted_mu
          FROM actuals a
        ),
        joined AS (
          SELECT
            p.item_id,
            p.rollup_date,
            p.units_sold,
            p.predicted_mu,
            COALESCE(c.name, '(uncategorized)') AS category
          FROM preds p
          JOIN products pr ON pr.id = p.item_id
          LEFT JOIN categories c ON c.id = pr.category_id
          WHERE p.predicted_mu IS NOT NULL
        )
        SELECT
          category,
          COUNT(*)::bigint                                                   AS scored_item_days,
          COALESCE(SUM(ABS(predicted_mu - units_sold)), 0)                   AS sum_abs_error,
          COALESCE(SUM(units_sold), 0)::bigint                               AS sum_actual,
          COALESCE(SUM(predicted_mu - units_sold), 0)                        AS sum_signed_error,
          AVG(CASE WHEN units_sold > 0
                   THEN ABS(predicted_mu - units_sold) / units_sold
                   ELSE NULL END)                                            AS mape_sale_days,
          COUNT(*) FILTER (WHERE predicted_mu < units_sold)::bigint          AS under_count,
          COUNT(*) FILTER (WHERE predicted_mu > units_sold)::bigint          AS over_count
        FROM joined
        GROUP BY category
        ORDER BY sum_actual DESC
        """, nativeQuery = true)
    List<Object[]> aggregateAccuracyByCategory(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}

