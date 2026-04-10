package com.mirai.inventoryservice.dtos.assistant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tiny (~500 byte) header payload injected into every Product Assistant
 * chat turn. Contains only the fields the LLM needs to answer the majority
 * of single-product questions without a tool call.
 */
public record HeaderBundleDTO(
        UUID productId,
        String productName,
        String categoryName,
        Integer currentStock,
        Integer unitsSoldLast30,
        Integer unitsSoldPrior30,
        BigDecimal velocity,
        BigDecimal daysToStockout,
        BigDecimal forecastConfidence,
        BigDecimal mape,
        OffsetDateTime lastRestockAt,
        Integer damageCountLast30,
        boolean onDisplay
) {}
