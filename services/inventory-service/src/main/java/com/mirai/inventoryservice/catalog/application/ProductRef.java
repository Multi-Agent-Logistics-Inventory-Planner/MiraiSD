package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.KujiType;
import com.mirai.inventoryservice.catalog.domain.Product;

import java.util.UUID;

/**
 * Immutable, catalog-external view of a {@link Product}. Never carries {@code unitCost}/
 * {@code msrp} — those are {@link CatalogPricing}'s concern, isolated so money access stays
 * greppable and role-gate-able (docs: .specs/phase-5b-catalog-facade/spec.md, Product decisions).
 *
 * <p>{@code categoryId} only (no category name): resolving the name is the caller's job via
 * {@link CatalogQueries#allCategoryRefs()} (bulk, e.g. {@code AnalyticsService}'s
 * every-category-once-then-map pattern) or {@link CatalogQueries#findCategoryById(java.util.UUID)}
 * (single, e.g. {@code ProductReportBundleService}'s one-category-per-report lookup — it reads
 * {@code product.getCategory().getName()} directly today, which a full {@code allCategoryRefs()}
 * table scan would wastefully replace for a single-product report). Both shapes exist precisely
 * so neither consumer is forced into the other's access pattern.
 */
public record ProductRef(
        UUID id,
        String sku,
        String name,
        String imageUrl,
        Boolean isActive,
        Integer quantity,
        String letter,
        Integer templateQuantity,
        Integer packsPerBox,
        UUID parentId,
        KujiType kujiType,
        String kujiSlackWebhookUrl,
        Integer reorderPoint,
        Integer targetStockLevel,
        Integer leadTimeDays,
        Boolean forecastingEnabled,
        UUID categoryId,
        UUID preferredSupplierId,
        Boolean preferredSupplierAuto
) {

    public static ProductRef from(Product product) {
        return new ProductRef(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getImageUrl(),
                product.getIsActive(),
                product.getQuantity(),
                product.getLetter(),
                product.getTemplateQuantity(),
                product.getPacksPerBox(),
                product.getParentId(),
                product.getKujiType(),
                product.getKujiSlackWebhookUrl(),
                product.getReorderPoint(),
                product.getTargetStockLevel(),
                product.getLeadTimeDays(),
                product.getForecastingEnabled(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getPreferredSupplierId(),
                product.getPreferredSupplierAuto()
        );
    }
}
