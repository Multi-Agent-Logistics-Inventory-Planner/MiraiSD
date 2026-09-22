package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * Master-identity-only response body for the global {@code /api/v1/catalog/products} routes.
 * Carries no per-site value (isActive, unitCost, msrp, reorderPoint, targetStockLevel,
 * leadTimeDays, forecastingEnabled, quantity) from either site (spec.md AC-5 step 6) - those live
 * only behind {@code /api/v1/sites/{siteId}/products/{productId}/{assortment,settings}}.
 *
 * <p>{@code hasChildren}, {@code preferredSupplier*}, the full {@code category} object, and
 * {@code kujiSlackWebhookUrl} are global catalog facts (not site-owned), added so this response
 * can eventually replace the legacy {@code GET /api/products} list for web callers - see
 * .specs/catalog-product-egress/spec.md AC-4. {@code hasActiveBox} is deliberately not included:
 * {@code kuji_boxes} has no {@code site_id} yet, so today's value is global and would change
 * meaning once site-scoped (owned by the Phase 7 kuji slice).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProductResponse {
    private UUID id;
    private String sku;
    private UUID categoryId;
    private String categoryName;
    private UUID parentId;
    private String letter;
    private Integer templateQuantity;
    private KujiType kujiType;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Omitted when the caller lacks the products:update permission - "
            + "redacted server-side (it is a Slack incoming-webhook bearer credential).")
    private String kujiSlackWebhookUrl;
    private Integer packsPerBox;
    private String name;
    private String description;
    private String imageUrl;
    private String notes;
    private UUID preferredSupplierId;
    private String preferredSupplierName;
    private Boolean preferredSupplierAuto;
    private CategoryResponseDTO category;
    private Boolean hasChildren;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * Constructor used by the JPQL {@code SELECT new CatalogProductResponse(...)} list projection
     * (see {@code ProductRepository.CATALOG_LIST_SELECT}). Flat argument list, mirroring
     * {@code ProductListItemDTO}'s precedent: {@code category} is built here from flat columns and
     * {@code hasChildren} arrives precomputed (a correlated EXISTS in the query itself), rather
     * than requiring the caller to know how to assemble them from raw query output.
     */
    public CatalogProductResponse(
            UUID id,
            String sku,
            UUID categoryId,
            String categoryName,
            UUID parentId,
            String letter,
            Integer templateQuantity,
            KujiType kujiType,
            String kujiSlackWebhookUrl,
            Integer packsPerBox,
            String name,
            String description,
            String imageUrl,
            String notes,
            UUID preferredSupplierId,
            String preferredSupplierName,
            Boolean preferredSupplierAuto,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID categoryParentId,
            String categorySlug,
            Integer categoryDisplayOrder,
            Boolean categoryIsActive,
            Boolean categoryUsesPacks,
            Boolean hasChildren
    ) {
        this.id = id;
        this.sku = sku;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.parentId = parentId;
        this.letter = letter;
        this.templateQuantity = templateQuantity;
        this.kujiType = kujiType;
        this.kujiSlackWebhookUrl = kujiSlackWebhookUrl;
        this.packsPerBox = packsPerBox;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.notes = notes;
        this.preferredSupplierId = preferredSupplierId;
        this.preferredSupplierName = preferredSupplierName;
        this.preferredSupplierAuto = preferredSupplierAuto;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.hasChildren = hasChildren;
        this.category = categoryId == null ? null : CategoryResponseDTO.builder()
                .id(categoryId)
                .name(categoryName)
                .parentId(categoryParentId)
                .slug(categorySlug)
                .displayOrder(categoryDisplayOrder)
                .isActive(categoryIsActive)
                .usesPacks(categoryUsesPacks)
                .children(Collections.emptyList())
                .build();
    }
}
