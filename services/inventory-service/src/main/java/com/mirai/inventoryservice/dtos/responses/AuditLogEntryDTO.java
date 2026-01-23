package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
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
public class AuditLogEntryDTO {
    private Long id;
    private LocationType locationType;
    private UUID itemId;
    private String itemSku;
    private String itemName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer previousQuantity;
    private Integer currentQuantity;
    private Integer quantityChange;
    private StockMovementReason reason;
    private UUID actorId;
    private String actorName;
    private OffsetDateTime at;
}
