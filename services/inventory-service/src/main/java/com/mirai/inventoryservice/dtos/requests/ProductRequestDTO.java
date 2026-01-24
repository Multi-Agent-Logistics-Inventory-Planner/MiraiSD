package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {
    private String sku;

    @NotNull(message = "Category is required")
    private ProductCategory category;

    private ProductSubcategory subcategory;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private Integer reorderPoint;

    private Integer targetStockLevel;

    private Integer leadTimeDays;

    private BigDecimal unitCost;

    @Pattern(
        regexp = "^(https://[a-zA-Z0-9-]+\\.supabase\\.co/storage/v1/object/public/product-images/[a-zA-Z0-9._-]+)?$",
        message = "Image URL must be from Supabase storage"
    )
    private String imageUrl;

    private String notes;
}
