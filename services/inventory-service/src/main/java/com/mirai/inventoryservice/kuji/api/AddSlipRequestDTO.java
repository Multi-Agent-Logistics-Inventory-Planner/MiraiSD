package com.mirai.inventoryservice.kuji.api;

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
public class AddSlipRequestDTO {
    @NotNull
    private UUID actorId;

    @NotNull
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    /** When true, the added slips land in the inactive bucket. Defaults to false. */
    private Boolean inactive;
}
