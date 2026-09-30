package com.mirai.inventoryservice.lootbox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CoinAdjustmentRequestDTO(
        @NotNull UUID userId,
        @NotNull Integer delta,
        @NotBlank String reason
) {}
