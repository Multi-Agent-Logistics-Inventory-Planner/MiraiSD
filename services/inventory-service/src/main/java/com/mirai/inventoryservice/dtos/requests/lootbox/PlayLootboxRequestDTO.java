package com.mirai.inventoryservice.dtos.requests.lootbox;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Player-facing play payload. The crate to roll is selected by the client.
 * idempotencyKey lives on the Idempotency-Key header (not this body).
 */
public record PlayLootboxRequestDTO(
        @NotNull UUID crateId
) {}
