package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rolling accuracy of the demand-forecasting pipeline. Headline is lead-time
 * WAPE (14-day forward window): {@code Σ |window_pred - window_actual| /
 * Σ window_actual}. This matches the metric that actually drives the reorder
 * decision; daily WAPE is structurally bad on intermittent demand and is no
 * longer surfaced. MAPE is gone for the same reason.
 */
public record ForecastAccuracyDTO(
    Window headline,
    Window comparison,
    List<CategoryAccuracy> byCategory
) {
    public record Window(
        int days,
        long scoredWindows,
        BigDecimal ltWape,
        BigDecimal biasUnitsPerDay,
        BigDecimal totalActualUnits,
        long underPredictions,
        long overPredictions
    ) {}

    public record CategoryAccuracy(
        String category,
        long scoredWindows,
        BigDecimal ltWape,
        BigDecimal biasUnitsPerDay,
        BigDecimal totalActualUnits
    ) {}
}
