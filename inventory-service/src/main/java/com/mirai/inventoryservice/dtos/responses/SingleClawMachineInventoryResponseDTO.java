package com.mirai.inventoryservice.dtos.responses;

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
public class SingleClawMachineInventoryResponseDTO {
    private UUID id;
    private UUID singleClawMachineId;
    private String singleClawMachineCode;
    private UUID itemId;
    private String itemSku;
    private String itemName;
    private String itemCategory;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
