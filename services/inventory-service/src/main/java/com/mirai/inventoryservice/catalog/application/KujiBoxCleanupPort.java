package com.mirai.inventoryservice.catalog.application;

import java.util.UUID;

/**
 * Removes Kuji boxes and tiers that reference a product as the parent kuji, when that product is
 * being deleted.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code kuji}'s repositories directly;
 * implemented outside {@code catalog}, in {@code kuji.application}. See docs:
 * .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface KujiBoxCleanupPort {

    /** Deletes tiers first, then boxes (FK ordering); returns counts for logging. */
    Result deleteBoxesAndTiersForProduct(UUID productId);

    record Result(int deletedBoxes, int deletedTiers) {
    }
}
