package com.pm.inventoryservice.dtos.requests;

import com.pm.inventoryservice.models.enums.ProductCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinInventoryRequestDTO {

    @NotNull(message = "Category is required")
    private ProductCategory category;

    private String description;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be at least 0")
    private Integer quantity;
}

