package com.mirai.inventoryservice.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Slim, site-scoped inventory totals projection (.specs/phase-6-inventory 6c, T-6c-5, AC-5).
 * Deliberately carries no catalog metadata (sku/name/image/category) -- embedding a catalog type
 * here would create an {@code inventory.api -> catalog.api} dependency edge (F-6c-9/R-9). Callers
 * that need catalog metadata alongside these totals join it client-side against data they already
 * have from a catalog read, rather than this endpoint duplicating it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteInventoryTotalDTO {
    private UUID productId;
    private int totalQuantity;
    private OffsetDateTime lastUpdatedAt;
}
