package com.mirai.inventoryservice.lootbox.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCoinEconomyConfigRequestDTO(
        @NotNull @PositiveOrZero Integer reviewCoinRate
) {}
