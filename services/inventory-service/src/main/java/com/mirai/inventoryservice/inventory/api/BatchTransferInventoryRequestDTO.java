package com.mirai.inventoryservice.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code @Size(max = 50)} (.specs/phase-6-inventory 6d, T-6d-be-5, user-confirmed): matches
 * {@code BatchAdjustStockRequestDTO}'s existing cap and bounds per-transfer pessimistic-lock
 * footprint on the single 512 MB-heap deployment. A deliberate, user-approved tightening of this
 * shared DTO -- it also lowers the legacy {@code /api/stock-movements/batch-transfer} route's
 * accepted batch size; no known caller sends anywhere near 50.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransferInventoryRequestDTO {

    @NotNull(message = "Transfers list is required")
    @Size(min = 1, max = 50, message = "Between 1 and 50 transfers are required")
    private List<@Valid TransferInventoryRequestDTO> transfers;
}
