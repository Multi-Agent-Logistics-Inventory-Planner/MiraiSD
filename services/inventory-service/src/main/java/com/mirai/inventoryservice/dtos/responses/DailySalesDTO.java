package com.mirai.inventoryservice.dtos.responses;

public record DailySalesDTO(
    String date,
    Integer totalUnits
) {}
