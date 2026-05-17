package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAdjustLineDTO {

    @NotNull(message = "Inventory id is required")
    private UUID inventoryId;

    @NotNull(message = "Quantity change is required")
    private Integer quantityChange;

    /** Optional intake unit ("pack" or "box") preserved for audit-log readability. */
    private String intakeUnit;

    /** Raw quantity in the user's chosen unit. */
    private Integer intakeQty;
}
