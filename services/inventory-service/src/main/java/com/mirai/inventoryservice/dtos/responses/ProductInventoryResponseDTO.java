package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing all inventory entries for a specific product across all locations.
 * Used by the optimized product-inventory endpoint to reduce N+1 queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInventoryResponseDTO {
    private UUID productId;
    private String productSku;
    private String productName;
    private int totalQuantity;
    private List<ProductInventoryEntryDTO> entries;
}
