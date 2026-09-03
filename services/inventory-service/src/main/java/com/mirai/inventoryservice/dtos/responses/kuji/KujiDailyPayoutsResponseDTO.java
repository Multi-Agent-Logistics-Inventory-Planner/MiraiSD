package com.mirai.inventoryservice.dtos.responses.kuji;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

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
    private static final String REDACTED_NOTE =
            "Omitted when the caller lacks the kuji_prices:view permission - the value is redacted "
                    + "server-side, so clients must handle its absence rather than assuming a number.";

    public record DailyPoint(
            LocalDate date,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = REDACTED_NOTE) BigDecimal valueWon,
            Integer slipCount) {}

    public record Totals(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = REDACTED_NOTE) BigDecimal valueWon,
            Integer slipCount) {}
}
