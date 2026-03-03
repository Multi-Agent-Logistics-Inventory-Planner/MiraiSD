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
public class SetMachineDisplayRequestDTO {
    @NotNull(message = "Location type is required")
    private LocationType locationType;

    @NotNull(message = "Machine ID is required")
    private UUID machineId;

    @NotNull(message = "Product ID is required")
    private UUID productId;

    private UUID actorId;
}
