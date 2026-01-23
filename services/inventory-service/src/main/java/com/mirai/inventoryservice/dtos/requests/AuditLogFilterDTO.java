package com.mirai.inventoryservice.dtos.requests;

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
public class AuditLogFilterDTO {
    private String search;
    private UUID actorId;
    private StockMovementReason reason;
    private OffsetDateTime fromDate;
    private OffsetDateTime toDate;
}
