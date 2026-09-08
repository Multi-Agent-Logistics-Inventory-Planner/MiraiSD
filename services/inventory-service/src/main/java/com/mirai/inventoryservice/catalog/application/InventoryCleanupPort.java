package com.mirai.inventoryservice.catalog.application;

import java.util.Collection;
import java.util.UUID;

/**
 * Removes location-inventory rows and stock-movement history for a product being deleted.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code inventory}'s repositories
 * directly; implemented outside {@code catalog}, in {@code inventory.application}. See docs:
 * .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface InventoryCleanupPort {

    void deleteInventoryForProduct(UUID productId);

    void deleteInventoryForProducts(Collection<UUID> productIds);

    void deleteStockMovementsForProduct(UUID productId);

    void deleteStockMovementsForProducts(Collection<UUID> productIds);
}
