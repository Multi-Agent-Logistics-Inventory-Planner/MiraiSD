package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Unified response DTO for inventory at any location type.
 * Replaces the 9 type-specific DTOs (BoxBinInventoryResponseDTO, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationInventoryResponseDTO {
    private UUID id;
    private UUID locationId;
    private String locationCode;
    private String storageLocationType;
    private ProductSummaryDTO item;
    private Integer quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
