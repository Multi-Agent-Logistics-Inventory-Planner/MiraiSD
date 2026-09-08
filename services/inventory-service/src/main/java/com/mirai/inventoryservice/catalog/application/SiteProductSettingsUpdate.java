package com.mirai.inventoryservice.catalog.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The full settings-write contract for one {@code site_products} row (AC-6,
 * .specs/phase-5c-site-products).
 * <ul>
 *   <li>{@code expectedVersion} is required. {@code null} means the caller omitted it, which is
 *       rejected exactly like a stale version - a missing version never falls back to
 *       last-write-wins ({@link SiteProductService#updateSettings}).</li>
 *   <li>For the five nullable override fields, {@link FieldUpdate#omitted()} leaves the stored
 *       value untouched and {@code FieldUpdate.of(null)} clears the override back to global
 *       inheritance - a plain nullable field cannot distinguish these two (AC-6).</li>
 *   <li>{@code forecastingEnabled} is the one field this cannot apply to: the column is
 *       {@code NOT NULL}, so both {@link FieldUpdate#omitted()} and an explicit
 *       {@code FieldUpdate.of(null)} leave the row's current value unchanged rather than clearing
 *       anything.</li>
 * </ul>
 */
public record SiteProductSettingsUpdate(
        Long expectedVersion,
        FieldUpdate<Boolean> forecastingEnabled,
        FieldUpdate<BigDecimal> unitCost,
        FieldUpdate<BigDecimal> msrp,
        FieldUpdate<Integer> reorderPoint,
        FieldUpdate<Integer> targetStockLevel,
        FieldUpdate<Integer> leadTimeDays
) {
    public SiteProductSettingsUpdate {
        Objects.requireNonNull(forecastingEnabled, "forecastingEnabled must be FieldUpdate.omitted(), not null");
        Objects.requireNonNull(unitCost, "unitCost must be FieldUpdate.omitted(), not null");
        Objects.requireNonNull(msrp, "msrp must be FieldUpdate.omitted(), not null");
        Objects.requireNonNull(reorderPoint, "reorderPoint must be FieldUpdate.omitted(), not null");
        Objects.requireNonNull(targetStockLevel, "targetStockLevel must be FieldUpdate.omitted(), not null");
        Objects.requireNonNull(leadTimeDays, "leadTimeDays must be FieldUpdate.omitted(), not null");
    }
}
