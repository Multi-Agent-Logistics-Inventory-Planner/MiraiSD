package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCoinEconomyConfigRequestDTO(
        @NotNull @PositiveOrZero Integer reviewCoinRate
) {}
