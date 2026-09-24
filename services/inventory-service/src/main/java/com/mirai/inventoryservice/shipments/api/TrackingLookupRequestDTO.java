package com.mirai.inventoryservice.shipments.api;

import jakarta.validation.constraints.NotBlank;

public record TrackingLookupRequestDTO(
    @NotBlank(message = "Tracking number is required")
    String trackingNumber,

    String carrier // Optional - EasyPost can auto-detect
) {}
