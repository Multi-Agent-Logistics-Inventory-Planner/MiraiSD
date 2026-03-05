package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
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
public class SwapMachineDisplayRequestDTO {
    @NotNull(message = "Outgoing display ID is required")
    private UUID outgoingDisplayId;

    @NotNull(message = "Incoming product ID is required")
    private UUID incomingProductId;

    @NotNull(message = "Location type is required")
    private LocationType locationType;

    @NotNull(message = "Machine ID is required")
    private UUID machineId;

    private UUID actorId;
}
