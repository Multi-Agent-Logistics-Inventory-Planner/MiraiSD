package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.domain.KujiType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Master-identity-only response body for the global {@code /api/v1/catalog/products} routes.
 * Carries no per-site value (isActive, unitCost, msrp, reorderPoint, targetStockLevel,
 * leadTimeDays, forecastingEnabled, quantity) from either site (spec.md AC-5 step 6) - those live
 * only behind {@code /api/v1/sites/{siteId}/products/{productId}/{assortment,settings}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProductResponse {
    private UUID id;
    private String sku;
    private UUID categoryId;
    private String categoryName;
    private UUID parentId;
    private String letter;
    private Integer templateQuantity;
    private KujiType kujiType;
    private Integer packsPerBox;
    private String name;
    private String description;
    private String imageUrl;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
