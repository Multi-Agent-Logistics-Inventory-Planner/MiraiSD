package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.ActionCenterDTO;
import com.mirai.inventoryservice.dtos.responses.CategoryInventoryDTO;
import com.mirai.inventoryservice.dtos.responses.DemandLeadersDTO;
import com.mirai.inventoryservice.dtos.responses.InsightsDTO;
import com.mirai.inventoryservice.dtos.responses.PerformanceMetricsDTO;
import com.mirai.inventoryservice.dtos.responses.SalesSummaryDTO;
import com.mirai.inventoryservice.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

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
     * Consolidated action center - replaces 10+ separate queries.
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
     * Demand leaders by demand velocity and stock velocity - admin only.
     * Replaces top-sellers endpoint with demand-based metrics.
     * @param period Time period: 7d, 30d (default), 90d, ytd
     */
    @GetMapping("/demand-leaders")
    @PreAuthorize("hasRole('ADMIN')")
    public DemandLeadersDTO getDemandLeaders(@RequestParam(defaultValue = "30d") String period) {
        return analyticsService.getDemandLeaders(period);
    }
}
