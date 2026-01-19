package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponseDTO {
    private Long id;
    private LocationType locationType;
    private UUID itemId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantityChange;
    private StockMovementReason reason;
    private UUID actorId;
    private OffsetDateTime at;
    private Map<String, Object> metadata;
}

