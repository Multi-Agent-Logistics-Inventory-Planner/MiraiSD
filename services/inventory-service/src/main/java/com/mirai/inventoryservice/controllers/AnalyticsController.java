package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.assistant.ComparisonRowDTO;
import com.mirai.inventoryservice.dtos.assistant.DetailBundleDTO;
import com.mirai.inventoryservice.dtos.assistant.HeaderBundleDTO;
import com.mirai.inventoryservice.dtos.assistant.MovementRowDTO;
import com.mirai.inventoryservice.dtos.assistant.MovementSummaryDTO;
import com.mirai.inventoryservice.dtos.responses.ActionCenterDTO;
import com.mirai.inventoryservice.dtos.responses.CategoryInventoryDTO;
import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO;
import com.mirai.inventoryservice.dtos.responses.PerformanceMetricsDTO;
import com.mirai.inventoryservice.dtos.responses.SalesSummaryDTO;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.services.AnalyticsSeedService;
import com.mirai.inventoryservice.services.AnalyticsService;
import com.mirai.inventoryservice.services.ProductReportBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Validated
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsSeedService analyticsSeedService;
    private final ProductReportBundleService productReportBundleService;

    @GetMapping("/inventory-by-category")
    public List<CategoryInventoryDTO> getInventoryByCategory() {
        return analyticsService.getInventoryByCategory();
    }

    @GetMapping("/performance-metrics")
    public PerformanceMetricsDTO getPerformanceMetrics() {
        return analyticsService.getPerformanceMetrics();
    }

    @GetMapping("/sales-summary")
    public SalesSummaryDTO getSalesSummary() {
        return analyticsService.getSalesSummary();
    }

    /**
     * Recomputes daily sales rollups from stock movement data.
     * Use after MSRP updates to recalculate revenue/cost/profit values.
     * @param monthsBack Number of months to recompute (default 24, max 36)
     */
    @PostMapping("/recompute-rollups")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> recomputeRollups(
            @RequestParam(defaultValue = "24") @Min(1) @Max(36) int monthsBack) {
        int count = analyticsSeedService.recomputeAllRollups(monthsBack);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "rollupsRecomputed", count,
            "monthsBack", monthsBack
        ));
    }

    /**
     * Returns items needing reorder decisions, sorted by urgency.
     * Now includes demand velocity, volatility, and forecast accuracy.
     */
    @GetMapping("/action-center")
    public ActionCenterDTO getActionCenter() {
        return analyticsService.getActionCenter();
    }

    /**
     * Category performance and day-of-week patterns.
     * Uses demand-based metrics instead of revenue-based metrics.
     */
    @GetMapping("/insights")
    public InsightsDTO getInsights() {
        return analyticsService.getInsights();
    }

    /**
     * Demand leaders by demand velocity and stock velocity - admin and assistant manager.
     * Replaces top-sellers endpoint with demand-based metrics.
     * @param period Time period: 7d, 30d (default), 90d, ytd
     */
    @GetMapping("/demand-leaders")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public DemandLeadersDTO getDemandLeaders(@RequestParam(defaultValue = "30d") String period) {
        return analyticsService.getDemandLeaders(period);
    }

    // ========= Product Assistant endpoints (admin-only) =========

    /**
     * ~500 byte header bundle injected into every Product Assistant chat turn.
     * Cached in the Next.js chat route for 10s to avoid round-tripping on each turn.
     */
    @GetMapping("/products/{id}/report-bundle/header")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HeaderBundleDTO> getReportBundleHeader(@PathVariable("id") UUID productId) {
        HeaderBundleDTO body = productReportBundleService.getHeader(productId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.SECONDS).cachePrivate())
                .body(body);
    }

    /**
     * Full deterministic report bundle (~30 KB) powering the Product Assistant
     * report panel. Fetched exactly once per session on mount - chat tool calls
     * never hit this endpoint.
     */
    @GetMapping("/products/{id}/report-bundle/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DetailBundleDTO> getReportBundleDetail(
            @PathVariable("id") UUID productId,
            @RequestParam(name = "days", defaultValue = "90") @Min(1) @Max(365) int days) {
        DetailBundleDTO body = productReportBundleService.getDetail(productId, days);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.SECONDS).cachePrivate())
                .body(body);
    }

    /**
     * Stock movement drill-down for the Product Assistant tool loop. Returns
     * projection rows (no entity hydration). Limit capped at 200.
     */
    @GetMapping("/products/{id}/movements")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MovementRowDTO> getProductMovements(
            @PathVariable("id") UUID productId,
            @RequestParam("from") OffsetDateTime from,
            @RequestParam("to") OffsetDateTime to,
            @RequestParam(name = "reasons", required = false) String reasonsCsv,
            @RequestParam(name = "limit", defaultValue = "100") @Min(1) @Max(200) int limit) {
        List<StockMovementReason> reasons = parseReasons(reasonsCsv);
        return productReportBundleService.getMovements(productId, from, to, reasons, limit);
    }

    /**
     * Aggregate movement summary - answers "how many restocks last month?",
     * "when was the last damage event?", and "biggest sales day" in one kilobyte.
     */
    @GetMapping("/products/{id}/movements/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public MovementSummaryDTO getProductMovementSummary(
            @PathVariable("id") UUID productId,
            @RequestParam("from") OffsetDateTime from,
            @RequestParam("to") OffsetDateTime to) {
        return productReportBundleService.getMovementSummary(productId, from, to);
    }

    /**
     * Top-N category peers ranked by a supported metric.
     * Allowed metrics: sales_velocity, days_to_stockout.
     */
    @GetMapping("/products/{id}/comparison")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ComparisonRowDTO> getProductComparison(
            @PathVariable("id") UUID productId,
            @RequestParam("metric") String metric,
            @RequestParam(name = "limit", defaultValue = "5") @Min(1) @Max(20) int limit) {
        return productReportBundleService.getComparison(productId, metric, limit);
    }

    private static List<StockMovementReason> parseReasons(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .map(s -> {
                    try {
                        return StockMovementReason.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid movement reason: " + s);
                    }
                })
                .toList();
    }
}
