package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single place {@code COALESCE(site_products.X, products.X)} is computed (.specs/
 * phase-5c-site-products AC-4). Two distinct read paths, both resolved here so no caller
 * reimplements the fallback:
 * <ul>
 *   <li>{@link #absent} - no {@code site_products} row exists: never carried at this site,
 *       {@code isStocked = false}, every setting falls back to the global {@link Product}.</li>
 *   <li>{@link #from} - a row exists (carried, or de-assorted with overrides retained):
 *       {@code isStocked}/{@code forecastingEnabled} come directly from the row (both are
 *       {@code NOT NULL} columns, never coalesced); each nullable override column falls back to
 *       the global value only when that column itself is NULL on the row.</li>
 * </ul>
 */
public record EffectiveProductSettings(
        UUID siteId,
        UUID productId,
        boolean isStocked,
        boolean forecastingEnabled,
        BigDecimal unitCost,
        BigDecimal msrp,
        Integer reorderPoint,
        Integer targetStockLevel,
        Integer leadTimeDays
) {

    public static EffectiveProductSettings absent(UUID siteId, Product product) {
        return new EffectiveProductSettings(
                siteId,
                product.getId(),
                false,
                Boolean.TRUE.equals(product.getForecastingEnabled()),
                product.getUnitCost(),
                product.getMsrp(),
                product.getReorderPoint(),
                product.getTargetStockLevel(),
                product.getLeadTimeDays());
    }

    public static EffectiveProductSettings from(SiteProduct siteProduct, Product product) {
        return new EffectiveProductSettings(
                siteProduct.getSiteId(),
                product.getId(),
                Boolean.TRUE.equals(siteProduct.getIsStocked()),
                Boolean.TRUE.equals(siteProduct.getForecastingEnabled()),
                siteProduct.getUnitCost() != null ? siteProduct.getUnitCost() : product.getUnitCost(),
                siteProduct.getMsrp() != null ? siteProduct.getMsrp() : product.getMsrp(),
                siteProduct.getReorderPoint() != null ? siteProduct.getReorderPoint() : product.getReorderPoint(),
                siteProduct.getTargetStockLevel() != null ? siteProduct.getTargetStockLevel() : product.getTargetStockLevel(),
                siteProduct.getLeadTimeDays() != null ? siteProduct.getLeadTimeDays() : product.getLeadTimeDays());
    }
}
