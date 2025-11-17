package com.pm.inventoryservice.dtos.responses;

import com.pm.inventoryservice.models.enums.LocationType;
import com.pm.inventoryservice.models.enums.StockMovementReason;
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
    private UUID itemId;
    private LocationType locationType;
    private UUID fromBoxId;
    private UUID toBoxId;
    private Integer quantityChange;
    private StockMovementReason reason;
    private UUID actorId;
    private OffsetDateTime at;
    private Map<String, Object> metadata;

    // Optional: include resolved location codes for UI display
    private String fromLocationCode;  // e.g., "B1"
    private String toLocationCode;    // e.g., "S2"
}
