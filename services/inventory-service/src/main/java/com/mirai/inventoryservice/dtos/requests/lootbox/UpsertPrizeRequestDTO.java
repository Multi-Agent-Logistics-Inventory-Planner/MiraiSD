package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record UpsertPrizeRequestDTO(
        @NotNull UUID tierId,
        @NotBlank String name,
        String description,
        String imageUrl,
        Boolean active,
        @PositiveOrZero Integer quantity
) {}
