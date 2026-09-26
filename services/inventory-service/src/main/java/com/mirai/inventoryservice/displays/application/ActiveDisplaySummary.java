package com.mirai.inventoryservice.displays.application;

import com.mirai.inventoryservice.models.enums.LocationType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read projection of an active machine display for a product, exposed by {@link DisplayQueries}
 * so callers outside {@code displays} never see the {@code MachineDisplay} entity itself.
 */
public record ActiveDisplaySummary(
        UUID displayId,
        UUID locationId,
        LocationType locationType,
        UUID machineId,
        OffsetDateTime startedAt) {
}
