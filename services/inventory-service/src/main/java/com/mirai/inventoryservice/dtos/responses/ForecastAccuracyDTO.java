package com.mirai.inventoryservice.dtos.responses;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rolling accuracy of the demand-forecasting pipeline.
 *
 * WAPE is the headline (Sum |predicted - actual| / Sum actual). MAPE is kept for continuity
 * with the existing features.mape value but is computed only over days with actual sales > 0
 * because |error|/0 is undefined.
 */
public record ForecastAccuracyDTO(
    Window headline,
    Window comparison,
    List<CategoryAccuracy> byCategory
) {
    public record Window(
        int days,
        long scoredItemDays,
        BigDecimal wape,
        BigDecimal mape,
        BigDecimal bias,
        long totalActualUnits,
        long underPredictions,
        long overPredictions
    ) {}

    public record CategoryAccuracy(
        String category,
        long scoredItemDays,
        BigDecimal wape,
        BigDecimal mape,
        BigDecimal bias,
        long totalActualUnits
    ) {}
}
