package com.mirai.inventoryservice.dtos.requests;

import com.mirai.inventoryservice.models.enums.LocationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class SetMachineDisplayBatchRequestDTO {
    @NotNull(message = "Location type is required")
    private LocationType locationType;

    @NotNull(message = "Machine ID is required")
    private UUID machineId;

    @NotEmpty(message = "At least one product ID is required")
    private List<UUID> productIds;

    private UUID actorId;
}
