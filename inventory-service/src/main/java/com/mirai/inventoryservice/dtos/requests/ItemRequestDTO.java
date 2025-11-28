package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemRequestDTO {
    private String sku;

    @NotNull(message = "Category is required")
    private ProductCategory category;

    private ProductSubcategory subcategory;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @Min(value = 0, message = "Reorder point must be at least 0")
    private Integer reorderPoint;

    @Min(value = 0, message = "Target stock level must be at least 0")
    private Integer targetStockLevel;

    @Min(value = 0, message = "Lead time days must be at least 0")
    private Integer leadTimeDays;

    @Min(value = 0, message = "Unit cost must be at least 0")
    private BigDecimal unitCost;

    private Boolean isActive;

    private String imageUrl;

    private String notes;
}

