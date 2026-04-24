package com.mirai.inventoryservice.dtos.responses;

import com.mirai.inventoryservice.models.enums.CarrierStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TrackingLookupResponseDTO(
    String trackingNumber,
    String carrier,
    String status,
    CarrierStatus carrierStatus, // Logistics state derived from EasyPost status
    LocalDate dateOrdered,       // From your order system (if found)
    LocalDate expectedDelivery,  // ETA from EasyPost
    LocalDate actualDelivery,
    String statusDetail,
    List<TrackingEventDTO> events,
    OffsetDateTime lastUpdated
) {}
