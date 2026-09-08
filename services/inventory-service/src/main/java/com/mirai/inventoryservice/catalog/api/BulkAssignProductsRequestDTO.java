package com.mirai.inventoryservice.catalog.api;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkAssignProductsRequestDTO {
    @NotEmpty(message = "Product IDs list cannot be empty")
    private List<UUID> productIds;
}
