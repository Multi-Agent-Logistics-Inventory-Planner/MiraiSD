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
public class PusherMachineInventoryResponseDTO {
    private UUID id;
    private UUID pusherMachineId;
    private String pusherMachineCode;
    private ProductSummaryDTO item;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
