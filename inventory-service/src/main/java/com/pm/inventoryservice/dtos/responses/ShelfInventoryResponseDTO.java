package com.pm.inventoryservice.dtos.responses;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ShelfInventoryResponseDTO {
    private String id;
    private String shelfId;
    private String shelfCode;
    private String category;
    private String subcategory;
    private String description;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

