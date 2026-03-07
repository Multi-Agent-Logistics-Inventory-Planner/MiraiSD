package com.mirai.inventoryservice.dtos.requests;

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

    /** Prize letter for Kuji children (e.g., A, B, C). Max 2 chars. */
    @Size(max = 2)
    private String letter;

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
