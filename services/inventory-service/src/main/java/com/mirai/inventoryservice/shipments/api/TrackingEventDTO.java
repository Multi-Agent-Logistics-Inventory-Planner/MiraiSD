package com.mirai.inventoryservice.shipments.api;

import java.time.OffsetDateTime;

public record TrackingEventDTO(
    String status,
    String message,
    String location,
    OffsetDateTime occurredAt
) {}
