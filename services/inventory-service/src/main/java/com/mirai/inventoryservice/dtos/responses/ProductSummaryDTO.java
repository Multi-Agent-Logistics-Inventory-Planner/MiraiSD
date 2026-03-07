package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryDTO {
    private UUID id;
    private String sku;
    private String name;
    private CategoryResponseDTO category;
    private String imageUrl;
    private Boolean isActive;
    private Integer quantity;
    private UUID parentId;
    private String letter;
    private Boolean hasChildren;
}
