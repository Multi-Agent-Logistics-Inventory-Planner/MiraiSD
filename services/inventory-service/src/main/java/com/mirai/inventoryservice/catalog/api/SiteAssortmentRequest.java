package com.mirai.inventoryservice.catalog.api;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PUT /api/v1/sites/{siteId}/products/{productId}/assortment} - an
 * upsert (spec.md phase-5d AC-2), so this is deliberately the only field it needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteAssortmentRequest {
    @NotNull(message = "isStocked is required")
    private Boolean isStocked;
}
