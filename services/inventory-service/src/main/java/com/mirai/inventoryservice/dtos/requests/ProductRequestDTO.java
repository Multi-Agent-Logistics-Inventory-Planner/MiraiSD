package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {
    private String sku;

    /** Required for root products. Optional for prizes (inherits from parent). */
    private UUID categoryId;

    // Optional parent ID for creating child products (e.g., Kuji prizes)
    private UUID parentId;

    /** Prize letter or label for Kuji children (e.g., A, B, C, Last Prize). Max 50 chars. */
    @Size(max = 50)
    private String letter;

    /** Quantity per kuji set for prize products. Must be >= 0 when provided. */
    @Min(value = 0, message = "Template quantity must be 0 or greater")
    private Integer templateQuantity;

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

    private Integer initialStock;
}
