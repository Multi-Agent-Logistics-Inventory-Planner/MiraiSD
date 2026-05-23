package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.KujiType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * Slim product DTO for list endpoints. Excludes heavy/unused fields
 * (description, notes, parent entity, children, audit timestamps beyond updatedAt)
 * that the fat ProductResponseDTO carries for detail views.
 */
@Data
@NoArgsConstructor
public class ProductListItemDTO {
    private UUID id;
    private String sku;
    private String name;
    private String imageUrl;
    private Boolean isActive;
    private Integer quantity;
    private String letter;
    private Integer templateQuantity;
    private Integer packsPerBox;
    private UUID parentId;
    private KujiType kujiType;
    private String kujiSlackWebhookUrl;
    private Integer reorderPoint;
    private Integer targetStockLevel;
    private Integer leadTimeDays;
    private BigDecimal unitCost;
    private BigDecimal msrp;
    private UUID preferredSupplierId;
    private String preferredSupplierName;
    private Boolean preferredSupplierAuto;
    private CategoryResponseDTO category;
    private Boolean hasChildren;
    private OffsetDateTime updatedAt;

    /**
     * Constructor used by JPQL {@code SELECT new ProductListItemDTO(...)} projections.
     * Flat argument list so Hibernate can hydrate it without entity proxies.
     * {@code hasChildren} is filled in by the service via the parent-IDs set.
     */
    public ProductListItemDTO(
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
            BigDecimal unitCost,
            BigDecimal msrp,
            UUID preferredSupplierId,
            String preferredSupplierName,
            Boolean preferredSupplierAuto,
            OffsetDateTime updatedAt,
            UUID categoryId,
            String categoryName,
            UUID categoryParentId,
            String categorySlug,
            Integer categoryDisplayOrder,
            Boolean categoryIsActive,
            Boolean categoryUsesPacks
    ) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.imageUrl = imageUrl;
        this.isActive = isActive;
        this.quantity = quantity;
        this.letter = letter;
        this.templateQuantity = templateQuantity;
        this.packsPerBox = packsPerBox;
        this.parentId = parentId;
        this.kujiType = kujiType;
        this.kujiSlackWebhookUrl = kujiSlackWebhookUrl;
        this.reorderPoint = reorderPoint;
        this.targetStockLevel = targetStockLevel;
        this.leadTimeDays = leadTimeDays;
        this.unitCost = unitCost;
        this.msrp = msrp;
        this.preferredSupplierId = preferredSupplierId;
        this.preferredSupplierName = preferredSupplierName;
        this.preferredSupplierAuto = preferredSupplierAuto;
        this.updatedAt = updatedAt;
        this.hasChildren = null;
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
