package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.CategoryInventoryDTO;
import com.mirai.inventoryservice.dtos.responses.PerformanceMetricsDTO;
import com.mirai.inventoryservice.dtos.responses.SalesSummaryDTO;
import com.mirai.inventoryservice.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
