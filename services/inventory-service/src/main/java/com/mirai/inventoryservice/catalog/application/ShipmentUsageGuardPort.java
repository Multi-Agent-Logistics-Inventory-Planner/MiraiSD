package com.mirai.inventoryservice.catalog.application;

import java.util.UUID;

/**
 * Reports whether a product is referenced by any shipment, so a product delete can be blocked
 * before it would otherwise fail on a foreign-key constraint.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code shipments}' repository directly;
 * implemented outside {@code catalog}, in {@code shipments.application}. See docs:
 * .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface ShipmentUsageGuardPort {

    /** True if {@code productId} appears as a line item on any shipment. */
    boolean isUsedInShipment(UUID productId);
}
