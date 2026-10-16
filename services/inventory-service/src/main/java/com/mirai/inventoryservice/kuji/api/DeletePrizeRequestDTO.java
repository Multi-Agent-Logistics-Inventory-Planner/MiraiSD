package com.mirai.inventoryservice.kuji.api;

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
public class DeletePrizeRequestDTO {
    @NotNull
    private UUID actorId;

    /** When true (default), decrement activeCount. When false, decrement inactiveCount. */
    private Boolean fromActive;
}
