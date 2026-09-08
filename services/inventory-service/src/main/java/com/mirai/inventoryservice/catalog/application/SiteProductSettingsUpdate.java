package com.mirai.inventoryservice.catalog.application;

import java.math.BigDecimal;

/**
 * A full replacement of a {@link com.mirai.inventoryservice.catalog.domain.SiteProduct}'s
 * override columns. For the five nullable override columns, {@code null} always means "no
 * override, inherit from the global product." {@code forecastingEnabled} is the one field this
 * cannot apply to - the column is {@code NOT NULL} - so a {@code null} there leaves the row's
 * current value unchanged rather than clearing anything.
 * <p>
 * The tri-state null-clears-override vs. omitted-leaves-unchanged distinction for the other five
 * fields, staleness/version checking, and the {@code 409} contract are AC-6 (T-4b) - this
 * baseline gives T-4b something to layer that contract onto rather than defining it here.
 */
public record SiteProductSettingsUpdate(
        Boolean forecastingEnabled,
        BigDecimal unitCost,
        BigDecimal msrp,
        Integer reorderPoint,
        Integer targetStockLevel,
        Integer leadTimeDays
) {
}
