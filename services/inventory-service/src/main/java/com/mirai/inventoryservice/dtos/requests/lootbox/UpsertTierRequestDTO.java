package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tier upsert payload. `lootboxId` is required on create but ignored on patch (tiers can't
 * be moved between crates today; that would require a dedicated re-parent endpoint).
 */
public record UpsertTierRequestDTO(
        UUID lootboxId,
        @NotBlank String name,
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal probabilityPct,
        String displayColor,
        Integer sortOrder,
        Boolean active
) {}
