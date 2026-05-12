package com.mirai.inventoryservice.dtos.requests.kuji;

import jakarta.validation.constraints.Min;
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
public class TransferInMoreRequestDTO {
    @NotNull
    private UUID actorId;

    /**
     * Source location to transfer from. Required for tiers linked to existing products.
     * Must be null when the tier's linked product is auto-created (a kuji child) — in
     * that case the backend mints inventory in place at the box location.
     */
    private UUID sourceLocationId;

    @NotNull
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    /** Optional intake unit ("pack" or "box") preserved on the kuji TRANSFER stock movements. */
    private String intakeUnit;

    /** Raw quantity in the user's chosen unit (e.g. 1 box → quantity=36 packs, intakeQty=1). */
    private Integer intakeQty;
}
