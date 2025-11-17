package com.pm.inventoryservice.dtos.responses;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class MachineInventoryResponseDTO {
    private String id;
    private String machineId;
    private String machineCode;
    private String category;
    private String subcategory;
    private String description;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
