package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.InventoryTotalDTO;
import com.mirai.inventoryservice.dtos.responses.ProductInventoryResponseDTO;
import com.mirai.inventoryservice.repositories.InventoryTotalsRepository;
import com.mirai.inventoryservice.services.InventoryAggregateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for aggregated inventory endpoints.
 * Provides optimized batch endpoints to reduce N+1 API calls from the frontend.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryAggregateController {

    private final InventoryAggregateService inventoryAggregateService;
    private final InventoryTotalsRepository inventoryTotalsRepository;

    public InventoryAggregateController(
            InventoryAggregateService inventoryAggregateService,
            InventoryTotalsRepository inventoryTotalsRepository) {
        this.inventoryAggregateService = inventoryAggregateService;
        this.inventoryTotalsRepository = inventoryTotalsRepository;
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
        List<InventoryTotalDTO> totals = inventoryTotalsRepository.findAllInventoryTotals();
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
        ProductInventoryResponseDTO response = inventoryAggregateService.getInventoryByProduct(productId);
        return ResponseEntity.ok(response);
    }
}
