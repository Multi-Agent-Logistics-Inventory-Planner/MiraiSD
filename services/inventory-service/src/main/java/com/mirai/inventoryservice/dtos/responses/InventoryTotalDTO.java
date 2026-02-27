package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing aggregated inventory totals for a single item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTotalDTO {
    private UUID itemId;
    private String sku;
    private String name;
    private String imageUrl;
    private String category;
    private String subcategory;
    private Double unitCost;
    private Boolean isActive;
    private int totalQuantity;
    private OffsetDateTime lastUpdatedAt;
}
