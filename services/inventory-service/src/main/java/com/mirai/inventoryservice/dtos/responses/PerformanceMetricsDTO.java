package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;

public record PerformanceMetricsDTO(
    BigDecimal turnoverRate,
    BigDecimal forecastAccuracy,
    BigDecimal stockoutRate,
    BigDecimal fillRate
) {}
