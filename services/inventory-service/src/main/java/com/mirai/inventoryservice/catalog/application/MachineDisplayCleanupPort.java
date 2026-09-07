package com.mirai.inventoryservice.catalog.application;

import java.util.Collection;
import java.util.UUID;

/**
 * Removes machine display assignments for a product being deleted.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code displays}' repository directly;
 * implemented outside {@code catalog}, in {@code displays.application}. See docs:
 * .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface MachineDisplayCleanupPort {

    void deleteDisplaysForProduct(UUID productId);

    void deleteDisplaysForProducts(Collection<UUID> productIds);
}
