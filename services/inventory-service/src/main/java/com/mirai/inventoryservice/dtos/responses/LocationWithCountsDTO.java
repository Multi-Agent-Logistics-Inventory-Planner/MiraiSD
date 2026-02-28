package com.mirai.inventoryservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a storage location with its inventory counts.
 * Used by the optimized locations-with-counts endpoint to reduce N+1 queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationWithCountsDTO {
    private UUID id;
    private String locationType;
    private String locationCode;
    private int inventoryRecords;
    private int totalQuantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
