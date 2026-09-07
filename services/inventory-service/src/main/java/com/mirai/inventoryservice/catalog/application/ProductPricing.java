package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cost/MSRP for one product, isolated from {@link ProductRef} so money access stays greppable
 * and role-gate-able (docs: .specs/phase-5b-catalog-facade/spec.md, Product decisions). Callers
 * are still responsible for their own role-based redaction (see
 * {@code catalog.api.CostVisibilityPolicy}) — this record carries the raw values.
 */
public record ProductPricing(UUID productId, BigDecimal unitCost, BigDecimal msrp) {

    public static ProductPricing from(Product product) {
        return new ProductPricing(product.getId(), product.getUnitCost(), product.getMsrp());
    }
}
