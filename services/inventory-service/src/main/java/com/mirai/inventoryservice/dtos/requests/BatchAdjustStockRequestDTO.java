package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAdjustStockRequestDTO {

    @NotNull(message = "Location type is required")
    private LocationType locationType;

    @NotNull(message = "Location id is required")
    private UUID locationId;

    @NotNull(message = "Adjustments list is required")
    @Size(min = 1, max = 50, message = "Batch must contain 1-50 adjustments")
    private List<@Valid BatchAdjustLineDTO> adjustments;

    @NotNull(message = "Reason is required")
    private StockMovementReason reason;

    private UUID actorId;

    private String notes;
}
