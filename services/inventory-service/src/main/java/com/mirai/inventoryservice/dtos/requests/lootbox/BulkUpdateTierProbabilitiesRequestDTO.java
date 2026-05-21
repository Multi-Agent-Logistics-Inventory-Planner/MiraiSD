package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BulkUpdateTierProbabilitiesRequestDTO(
        @NotNull UUID lootboxId,
        @NotEmpty @Valid List<TierProbability> tiers
) {
    public record TierProbability(
            @NotNull UUID id,
            @NotNull
            @DecimalMin("0.00")
            @DecimalMax("100.00")
            BigDecimal probabilityPct
    ) {}
}
