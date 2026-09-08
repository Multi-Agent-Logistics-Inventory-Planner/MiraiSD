package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;

/**
 * Records a product's initial stock, with tracking (audit log, stock movement, outbox event),
 * when the caller creates a parent product with a nonzero starting quantity.
 *
 * <p>Declared here so {@code catalog} does not depend on {@code inventory}'s tracked-inventory
 * machinery directly; implemented outside {@code catalog} by the module that owns that
 * machinery. See docs: .specs/phase-5a-catalog-module-move/spec.md AC-2.
 */
public interface InitialStockPort {

    /**
     * Persists {@code quantity} as the product's starting stock, in the same transaction as the
     * caller, generating whatever audit/tracking records the inventory layer requires.
     *
     * @param product  the already-persisted parent product to stock
     * @param quantity a positive initial quantity
     */
    void recordInitialStock(Product product, int quantity);
}
