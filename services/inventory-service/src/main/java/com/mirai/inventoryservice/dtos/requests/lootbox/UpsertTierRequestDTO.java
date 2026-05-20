package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertTierRequestDTO(
        @NotBlank String name,
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal probabilityPct,
        String displayColor,
        Integer sortOrder,
        Boolean active
) {}
