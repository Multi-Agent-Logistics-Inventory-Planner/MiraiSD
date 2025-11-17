package com.pm.inventoryservice.dtos.requests;

import com.pm.inventoryservice.models.enums.LocationType;
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
public class TransferInventoryRequestDTO {
    @NotNull
    private LocationType sourceLocationType; // MACHINE, BIN, SHELF

    @NotNull
    private UUID sourceInventoryId;

    @NotNull
    private LocationType destinationLocationType;

    @NotNull
    private UUID destinationInventoryId;

    @NotNull
    @Min(1)
    private Integer quantity; // Amount to transfer

    @NotNull
    private UUID actorId; // User performing the transfer

    private String notes; // Optional reason/description
}
