package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.models.enums.ProductSubcategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxBinInventoryResponseDTO {
    private UUID id;
    private UUID boxBinId;
    private String boxBinCode;
    private ProductCategory category;
    private ProductSubcategory subcategory;
    private String description;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

