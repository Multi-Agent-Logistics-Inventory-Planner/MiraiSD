package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private UUID id;
    private String sku;
    private ProductCategory category;
    private ProductSubcategory subcategory;
    private String name;
    private String description;
    private Integer reorderPoint;
    private Integer targetStockLevel;
    private Integer leadTimeDays;
    private BigDecimal unitCost;
    private Boolean isActive;
    private String imageUrl;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
