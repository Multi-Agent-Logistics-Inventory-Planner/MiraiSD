package com.mirai.inventoryservice.catalog.application;

import java.math.BigDecimal;

/**
 * The fields a legacy {@code /api/products} write actually changed, in the same
 * null-means-not-provided sense {@code ProductService}'s own request parameters already use for
 * {@code products.*}. Used only by {@link SiteProductService#syncExistingMainOverrides} - the
 * reverse direction of AC-5's compatibility - which is unversioned and never clears an override
 * (a legacy write can only sync a field MAIN already overrides, never manufacture or clear one).
 * This is deliberately a separate, simpler type from {@link SiteProductSettingsUpdate}: that
 * record is AC-6's versioned, tri-state settings-write contract for the site-facing endpoint, and
 * conflating the two would force this internal sync to carry a version and omitted/clear
 * distinction it has no use for.
 */
public record ProductFieldChanges(
        Boolean forecastingEnabled,
        BigDecimal unitCost,
        BigDecimal msrp,
        Integer reorderPoint,
        Integer targetStockLevel,
        Integer leadTimeDays
) {
}
