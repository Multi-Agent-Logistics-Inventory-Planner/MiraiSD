package com.mirai.inventoryservice.catalog.application;

import java.util.UUID;

/**
 * Looks up the most recent supplier that delivered a product, derived from shipment history.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code shipments}' repository directly;
 * implemented outside {@code catalog}. See docs: .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface SupplierDeliveryHistoryPort {

    /**
     * Returns {@code [supplierId (UUID), supplierDisplayName (String)]} for the most recent
     * delivered shipment containing {@code productId}, or {@code null} if none exists.
     */
    Object[] findLastDeliveredSupplier(UUID productId);
}
