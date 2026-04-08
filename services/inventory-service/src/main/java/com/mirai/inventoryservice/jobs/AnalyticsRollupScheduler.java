package com.mirai.inventoryservice.jobs;

import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.services.AnalyticsSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Scheduled job for refreshing analytics rollup tables.
 * Runs nightly to compute daily and monthly aggregations.
 *
 * In production, this would be replaced by a Supabase Edge Function
 * or pg_cron job running closer to the database.
 *
 * Only active in dev profile to avoid conflicts with production jobs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class AnalyticsRollupScheduler {

    private final AnalyticsSeedService analyticsSeedService;
    private final ForecastPredictionRepository forecastPredictionRepository;

    /**
     * Nightly job to refresh daily rollups.
     * Runs at 1:45 AM every day.
     * Processes the last 7 days to catch any late-arriving data.
     */
    @Scheduled(cron = "0 45 1 * * *")
    public void refreshDailyRollups() {
        log.info("Starting nightly daily rollups refresh");
        try {
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(7);

            analyticsSeedService.computeRollupsFromExistingData(startDate, today);

            log.info("Completed nightly daily rollups refresh for {} to {}", startDate, today);
        } catch (Exception e) {
            log.error("Error during nightly daily rollups refresh", e);
        }
    }

    /**
     * Snapshot forecast data for demand-based metrics.
     * Runs at 2:00 AM every day.
     * Captures mu_hat, sigma_d_hat, confidence, mape from forecast features.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void snapshotForecastData() {
        log.info("Starting nightly forecast snapshot");
        try {
            int count = analyticsSeedService.snapshotForecastData();
            log.info("Completed nightly forecast snapshot: {} snapshots created", count);
        } catch (Exception e) {
            log.error("Error during nightly forecast snapshot", e);
        }
    }

    /**
     * Cleanup overdue forecast predictions.
     * Runs at 2:30 AM every day.
     * Deletes forecasts where suggested_order_date has passed.
     * Fresh forecasts will be regenerated on the next pipeline run.
     */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void cleanupOverdueForecasts() {
        log.info("Starting overdue forecast cleanup");
        try {
            LocalDate today = LocalDate.now();
            int count = forecastPredictionRepository.deleteBySuggestedOrderDateBefore(today);
            log.info("Completed overdue forecast cleanup: {} forecasts deleted", count);
        } catch (Exception e) {
            log.error("Error during overdue forecast cleanup", e);
        }
    }

    /**
     * Rollup category demand metrics.
     * Runs at 2:15 AM every day.
     * Aggregates demand velocity, stock velocity, and risk counts by category.
     */
    @Scheduled(cron = "0 15 2 * * *")
    public void rollupCategoryDemand() {
        log.info("Starting nightly category demand rollup");
        try {
            int count = analyticsSeedService.rollupCategoryDemand();
            log.info("Completed nightly category demand rollup: {} rollups created", count);
        } catch (Exception e) {
            log.error("Error during nightly category demand rollup", e);
        }
    }

    /**
     * Weekly job to refresh monthly rollups.
     * Runs every Sunday at 3:00 AM.
     * Recomputes the current and previous month.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void refreshMonthlyRollups() {
        log.info("Starting weekly monthly rollups refresh");
        try {
            // Recompute last 2 months
            int monthsBack = 2;
            int count = analyticsSeedService.seedMonthlyRollups(monthsBack);

            log.info("Completed weekly monthly rollups refresh: {} rollups updated", count);
        } catch (Exception e) {
            log.error("Error during weekly monthly rollups refresh", e);
        }
    }

    /**
     * Monthly cleanup job.
     * Runs on the 1st of each month at 4:00 AM.
     * Removes rollups older than the retention period (12 months).
     */
    @Scheduled(cron = "0 0 4 1 * *")
    public void cleanupOldRollups() {
        log.info("Starting monthly rollups cleanup");
        try {
            // Keep 12 months of data
            LocalDate cutoffDate = LocalDate.now().minusMonths(12);

            // Note: The actual deletion is done in the repositories
            // This is a placeholder for the cleanup logic
            log.info("Would clean up rollups older than {}", cutoffDate);
        } catch (Exception e) {
            log.error("Error during monthly rollups cleanup", e);
        }
    }
}
