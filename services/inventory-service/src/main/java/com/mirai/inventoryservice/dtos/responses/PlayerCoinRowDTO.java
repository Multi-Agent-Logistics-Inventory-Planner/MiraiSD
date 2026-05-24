package com.mirai.inventoryservice.dtos.responses;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the admin Coins-tab player table. Sorted by balance desc on the server.
 *
 * `lastChangeDelta` / `lastChangeAt` describe the most recent coin movement (any
 * source: review credit, play, adjustment). Both null if the user has never had a
 * coin movement.
 */
@Builder
public record PlayerCoinRowDTO(
        UUID userId,
        String fullName,
        String email,
        long balance,
        Integer lastChangeDelta,
        OffsetDateTime lastChangeAt
) {}
