package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.inventory.application.InventoryAggregateService;
import com.mirai.inventoryservice.inventory.application.InventoryQueries;
import com.mirai.inventoryservice.identity.application.LegacyMainSiteContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for aggregated inventory endpoints.
 * Provides optimized batch endpoints to reduce N+1 API calls from the frontend.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryAggregateController {

    private final InventoryAggregateService inventoryAggregateService;
    private final InventoryQueries inventoryQueries;
    private final LegacyMainSiteContextResolver legacyMainSiteContextResolver;

    public InventoryAggregateController(
            InventoryAggregateService inventoryAggregateService,
            InventoryQueries inventoryQueries,
            LegacyMainSiteContextResolver legacyMainSiteContextResolver) {
        this.inventoryAggregateService = inventoryAggregateService;
        this.inventoryQueries = inventoryQueries;
        this.legacyMainSiteContextResolver = legacyMainSiteContextResolver;
    }

    /**
     * Get aggregated inventory totals for all products.
     * Returns total quantity and last updated time for each product across all location types.
     * This is a single-query replacement for the N+1 pattern of fetching inventory per location.
     *
     * @return List of inventory totals for all products
     */
    @GetMapping("/totals")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<InventoryTotalDTO>> getInventoryTotals() {
        var context = legacyMainSiteContextResolver.requireMain();
        List<InventoryTotalDTO> totals = inventoryQueries.findAllInventoryTotals();
        // Preserve the retained DTO's catalog fields while replacing its formerly global stock
        // values with MAIN-only totals. Missing rows are legitimate zero-stock products.
        Map<UUID, SiteInventoryTotalDTO> byProduct = inventoryQueries.findInventoryTotalsBySite(context.siteId())
                .stream().collect(Collectors.toMap(SiteInventoryTotalDTO::getProductId, row -> row));
        totals.forEach(total -> {
            SiteInventoryTotalDTO scoped = byProduct.get(total.getItemId());
            total.setTotalQuantity(scoped == null ? 0 : scoped.getTotalQuantity());
            total.setLastUpdatedAt(scoped == null ? null : scoped.getLastUpdatedAt());
        });
        // Authentication is read from the SecurityContext rather than taken as a method
        // parameter: keeps the redaction check colocated with the query, matching how this
        // method already worked before T-6 routed it through InventoryQueries.
        if (!RolePermissions.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(), Permission.COSTS_VIEW)) {
            totals.forEach(dto -> dto.setUnitCost(null));
        }
        return ResponseEntity.ok(totals);
    }

    /**
     * Get all inventory entries for a specific product across all location types.
     * Replaces the N+1 pattern of fetching inventory from each location individually.
     *
     * @param productId The product ID to look up inventory for
     * @return All inventory entries for the product with location details
     */
    @GetMapping("/by-product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ProductInventoryResponseDTO> getInventoryByProduct(
            @PathVariable UUID productId) {
        ProductInventoryResponseDTO response = inventoryAggregateService.getInventoryByProductAndSite(
                legacyMainSiteContextResolver.requireMain().siteId(), productId);
        return ResponseEntity.ok(response);
    }
}
