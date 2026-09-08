package com.mirai.inventoryservice.catalog.application;

import java.util.Collection;
import java.util.UUID;

/**
 * Purges existing forecast predictions for a product. Used both when a product's
 * {@code forecastingEnabled} flag transitions from {@code true} to {@code false} (single-product
 * purge, so stale predictions do not linger for a product forecasting is no longer computing) and
 * when a product is deleted (single or batch, for a parent's children).
 *
 * <p>Declared here so {@code catalog} does not depend on the forecasting layer's repository
 * directly; implemented outside {@code catalog}, in {@code analytics.application}. See docs:
 * .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface ForecastPurgePort {

    /** Deletes every forecast prediction row for {@code productId}, if any exist. */
    void purgeForecastsForProduct(UUID productId);

    /** Deletes every forecast prediction row for each of {@code productIds}, if any exist. */
    void purgeForecastsForProducts(Collection<UUID> productIds);
}
