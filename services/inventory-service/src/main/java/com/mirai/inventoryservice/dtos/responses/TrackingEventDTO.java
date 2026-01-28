package com.mirai.inventoryservice.dtos.responses;

import java.time.OffsetDateTime;

public record TrackingEventDTO(
    String status,
    String message,
    String location,
    OffsetDateTime occurredAt
) {}
