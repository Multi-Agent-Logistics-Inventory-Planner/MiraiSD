package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.LocationType;
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
public class ShipmentItemAllocationResponseDTO {
    private UUID id;
    private LocationType locationType;
    private UUID locationId;
    private Integer quantity;
    private OffsetDateTime receivedAt;
}
