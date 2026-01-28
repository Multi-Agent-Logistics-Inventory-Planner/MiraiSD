package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;

public record DailySalesDTO(
    String date,
    Integer totalUnits,
    BigDecimal totalRevenue
) {}
