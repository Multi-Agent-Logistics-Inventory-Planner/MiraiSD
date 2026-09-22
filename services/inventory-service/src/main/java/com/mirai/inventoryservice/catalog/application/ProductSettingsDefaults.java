package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Slim projection of the global-default columns {@link EffectiveProductSettings} needs to resolve
 * a site's settings for one product -- id/sku/name plus the six columns {@code site_products} can
 * override. Used by list reads that need every product's defaults but not the full {@link Product}
 * entity (no category/parent/description/notes/JOIN FETCH) -- see
 * {@code ProductRepository.findAllSettingsDefaults()} and
 * .specs/catalog-product-egress/spec.md AC-3.
 */
public record ProductSettingsDefaults(
        UUID id,
        String sku,
        String name,
        BigDecimal unitCost,
        BigDecimal msrp,
        Integer reorderPoint,
        Integer targetStockLevel,
        Integer leadTimeDays,
        Boolean forecastingEnabled
) {

    /**
     * Adapts a full {@link Product} entity to this slim shape, so {@link EffectiveProductSettings}
     * has exactly one implementation of the absent/from resolution logic regardless of whether the
     * caller already loaded a full entity (single-product reads) or the slim projection (list reads).
     */
    public static ProductSettingsDefaults from(Product product) {
        return new ProductSettingsDefaults(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnitCost(),
                product.getMsrp(),
                product.getReorderPoint(),
                product.getTargetStockLevel(),
                product.getLeadTimeDays(),
                product.getForecastingEnabled());
    }
}
