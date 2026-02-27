package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a single inventory entry for a product at a specific location.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInventoryEntryDTO {
    private UUID inventoryId;
    private String locationType;
    private UUID locationId;
    private String locationCode;
    private String locationLabel;
    private int quantity;
    private OffsetDateTime updatedAt;
}
