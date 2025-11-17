package com.pm.inventoryservice.dtos.responses;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class BinInventoryResponseDTO {
    private String id;
    private String binId;
    private String binCode;
    private String category;
    private String description;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

