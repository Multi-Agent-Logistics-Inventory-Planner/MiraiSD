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

    /**
     * For each calendar day in [startDate, endDate], returns the most recent
     * forecast_predictions row for the given item (max computed_at within the day).
     * Powers the per-day chart series on the Product Assistant detail bundle --
     * replaces the deleted analytics_forecast_snapshot table.
     */
    @Query(value = "SELECT fp.* FROM forecast_predictions fp "
            + "INNER JOIN (SELECT (computed_at AT TIME ZONE 'UTC')::date AS day, "
            + "MAX(computed_at) AS max_at FROM forecast_predictions "
            + "WHERE item_id = :itemId "
            + "AND (computed_at AT TIME ZONE 'UTC')::date >= :startDate "
            + "AND (computed_at AT TIME ZONE 'UTC')::date <= :endDate "
            + "GROUP BY (computed_at AT TIME ZONE 'UTC')::date) latest "
            + "ON fp.computed_at = latest.max_at "
            + "WHERE fp.item_id = :itemId "
            + "ORDER BY fp.computed_at ASC",
            nativeQuery = true)
    List<ForecastPrediction> findLatestPerDayByItemBetween(
        @Param("itemId") UUID itemId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

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
     * Lead-time WAPE aggregates by category over a rolling window. For each
     * (item, day) in the lookback we compute a forward 14-day sum of predicted
     * vs actual units, then aggregate ``Σ|window_pred − window_actual| /
     * Σ window_actual`` per category. Daily WAPE is misleading on intermittent
     * demand because per-day actuals are spiky; the lead-time sum is what
     * actually drives the reorder decision and is the metric the operator
     * cares about.
     *
     * Per-day predicted is mu_hat × DOW multiplier × event multipliers
     * (matching the production pipeline path). Windows extending beyond the
     * lookback's end date are dropped so trailing rows don't bias the metric.
     *
     * Note: ``analytics_daily_rollup`` is sparse (only sale-days produce rows
     * for an item), so window sums are over the SALE-DAYS in the 14-day
     * calendar span, not 14 dense rows. Bias is divided by the actual count
     * of observation-days to keep "units/day" honest.
     *
     * Returns rows of:
     *   [0] category            (String, "(uncategorized)" when null)
     *   [1] scored_windows      (Long)         number of 14-day windows aggregated
     *   [2] sum_abs_error       (BigDecimal)   Σ |window_pred - window_actual|
     *   [3] sum_actual          (BigDecimal)   Σ window_actual  (denominator of WAPE)
     *   [4] sum_signed_error    (BigDecimal)   Σ (window_pred - window_actual) for bias
     *   [5] sum_days_observed   (Long)         Σ days_in_window (denominator of bias)
     *   [6] under_count         (Long)         windows where pred_sum  < actual_sum
     *   [7] over_count          (Long)         windows where pred_sum  > actual_sum
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
                  * CASE WHEN EXISTS (
                      SELECT 1 FROM stock_movements sm
                      WHERE sm.item_id = a.item_id
                        AND sm.reason IN ('SHIPMENT_RECEIPT','SHIPMENT_PARTIAL_RECEIPT')
                        AND sm.at >= a.rollup_date - INTERVAL '7 days'
                        AND sm.at < a.rollup_date
                    ) THEN COALESCE(
                      (fp.features->'event_multipliers'->>'recent_shipment_7d')::numeric, 1.0
                    ) ELSE 1.0 END
                  * CASE WHEN EXISTS (
                      SELECT 1 FROM stock_movements sm
                      WHERE sm.item_id = a.item_id
                        AND sm.reason = 'DISPLAY_SET'
                        AND sm.at >= a.rollup_date - INTERVAL '7 days'
                        AND sm.at < a.rollup_date
                    ) THEN COALESCE(
                      (fp.features->'event_multipliers'->>'recent_display_7d')::numeric, 1.0
                    ) ELSE 1.0 END
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
            AND pr.forecasting_enabled = true
        ),
        windowed AS (
          -- Forward 14-day window sums per item. RANGE frame keyed on the
          -- date column so a gap in predictions doesn't shift later windows.
          -- We require both (a) the window end fits in the lookback and (b)
          -- the window contains 14 distinct days so partial windows don't
          -- skew the metric.
          SELECT
            category,
            item_id,
            rollup_date,
            SUM(units_sold) OVER w  AS actual_window,
            SUM(predicted_mu) OVER w AS predicted_window,
            COUNT(*) OVER w          AS days_in_window
          FROM joined
          WHERE rollup_date <= CAST(:endDate AS DATE) - INTERVAL '13 days'
          WINDOW w AS (
            PARTITION BY item_id
            ORDER BY rollup_date
            RANGE BETWEEN CURRENT ROW AND INTERVAL '13 days' FOLLOWING
          )
        )
        SELECT
          category,
          COUNT(*)::bigint                                                       AS scored_windows,
          COALESCE(SUM(ABS(predicted_window - actual_window)), 0)                AS sum_abs_error,
          COALESCE(SUM(actual_window), 0)                                        AS sum_actual,
          COALESCE(SUM(predicted_window - actual_window), 0)                     AS sum_signed_error,
          COALESCE(SUM(days_in_window), 0)::bigint                               AS sum_days_observed,
          COUNT(*) FILTER (WHERE predicted_window < actual_window)::bigint       AS under_count,
          COUNT(*) FILTER (WHERE predicted_window > actual_window)::bigint       AS over_count
        FROM windowed
        GROUP BY category
        ORDER BY sum_actual DESC
        """, nativeQuery = true)
    List<Object[]> aggregateAccuracyByCategory(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}

