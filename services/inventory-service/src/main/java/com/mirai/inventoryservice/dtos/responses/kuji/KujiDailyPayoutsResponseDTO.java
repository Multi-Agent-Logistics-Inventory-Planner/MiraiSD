package com.mirai.inventoryservice.dtos.responses.kuji;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Per-day net payouts (KUJI_PRIZE_WON minus KUJI_DRAW_REVERSED) for a box, bucketed by
 * the calendar day in the requested timezone. Series is always dense over [from, to].
 */
public record KujiDailyPayoutsResponseDTO(
        UUID boxId,
        LocalDate from,
        LocalDate to,
        String tz,
        List<DailyPoint> series,
        Totals total
) {
    public record DailyPoint(LocalDate date, BigDecimal valueWon, Integer slipCount) {}
    public record Totals(BigDecimal valueWon, Integer slipCount) {}
}
