package com.mirai.inventoryservice.dtos.requests;

import jakarta.validation.constraints.NotBlank;

public record TrackingLookupRequestDTO(
    @NotBlank(message = "Tracking number is required")
    String trackingNumber,

    String carrier // Optional - EasyPost can auto-detect
) {}
