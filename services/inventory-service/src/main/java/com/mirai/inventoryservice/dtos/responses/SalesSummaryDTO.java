package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummaryDTO(
    List<MonthlySalesDTO> monthlySales,
    List<DailySalesDTO> dailySales,
    BigDecimal totalRevenue,
    BigDecimal totalCost,
    BigDecimal totalProfit,
    Integer totalUnits,
    String periodStart,
    String periodEnd
) {}
