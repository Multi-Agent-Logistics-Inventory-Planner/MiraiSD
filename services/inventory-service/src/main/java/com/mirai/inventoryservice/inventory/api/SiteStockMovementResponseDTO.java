package com.mirai.inventoryservice.inventory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Site-scoped movement/audit-log row for the v1 {@code GET .../inventory/movements} route
 * (.specs/phase-6-inventory 6c, T-6c-11, Q-6c-5). {@code siteAttribution} is set to
 * {@code "UNKNOWN"} for a row whose underlying {@code stock_movements.site_id} is still null
 * (the pre-backfill compatibility window, .specs/phase-6-inventory 6b) and omitted otherwise --
 * per the resolved Q-6c-5 decision, such rows are included and labeled, never hidden, so the
 * audit trail stays complete. Remove this marker path once Q-6c-1's production backfill/deploy
 * is separately confirmed complete (tracked debt, not closed by this record).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteStockMovementResponseDTO {
    private Long id;
    private LocationType locationType;
    private UUID itemId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantityChange;
    private StockMovementReason reason;
    private UUID actorId;
    private OffsetDateTime at;
    private Map<String, Object> metadata;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "\"UNKNOWN\" when this row predates the site backfill; omitted otherwise.")
    private String siteAttribution;
}
