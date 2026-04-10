package com.mirai.inventoryservice.dtos.assistant;

import java.math.BigDecimal;
import java.util.UUID;

public record ComparisonRowDTO(
        UUID productId,
        String productName,
        BigDecimal metricValue,
        int rank
) {}
