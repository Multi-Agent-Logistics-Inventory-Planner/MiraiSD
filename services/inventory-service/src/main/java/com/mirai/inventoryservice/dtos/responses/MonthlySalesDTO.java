package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;

public record MonthlySalesDTO(
    String month,
    BigDecimal totalRevenue,
    BigDecimal totalCost,
    BigDecimal totalProfit,
    Integer totalUnits
) {}
