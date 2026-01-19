package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import com.mirai.inventoryservice.validation.ValidInventoryRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidInventoryRequest
public class InventoryRequestDTO {
    @NotNull(message = "Category is required")
    private ProductCategory category;

    // Subcategory is ONLY required for BLIND_BOX category, optional for all others
    private ProductSubcategory subcategory;

    private String description;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be at least 0")
    private Integer quantity;
}

