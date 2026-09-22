package com.mirai.inventoryservice.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single product's on-hand quantity at one location, for the v1
 * {@code GET .../inventory/locations/{locationId}} route (.specs/phase-6-inventory 6c, T-6c-11).
 * Deliberately carries no catalog metadata (sku/name/image) -- embedding a catalog type here
 * would create an {@code inventory.api -> catalog.api} dependency edge (F-6c-9/R-9), the same
 * reason {@link SiteInventoryTotalDTO} stays slim.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteLocationInventoryEntryDTO {
    private UUID inventoryId;
    private UUID productId;
    private int quantity;
    private OffsetDateTime updatedAt;
}
