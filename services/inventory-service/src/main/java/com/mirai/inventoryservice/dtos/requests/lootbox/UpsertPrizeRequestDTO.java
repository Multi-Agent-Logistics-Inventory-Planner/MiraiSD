package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpsertPrizeRequestDTO(
        @NotNull UUID tierId,
        @NotBlank String name,
        String description,
        String imageUrl,
        Boolean active
) {}
