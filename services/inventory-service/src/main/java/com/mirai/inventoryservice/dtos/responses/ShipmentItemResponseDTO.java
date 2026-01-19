package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.LocationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentItemResponseDTO {
    private UUID id;
    private ProductSummaryDTO item;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private BigDecimal unitCost;
    private LocationType destinationLocationType;
    private UUID destinationLocationId;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
