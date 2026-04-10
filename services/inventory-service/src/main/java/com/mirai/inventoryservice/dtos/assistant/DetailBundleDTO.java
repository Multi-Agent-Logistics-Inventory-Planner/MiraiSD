package com.mirai.inventoryservice.dtos.assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ~30 KB deterministic bundle powering the Product Assistant report panel.
 * Fetched exactly once per session on mount; never by chat tool calls.
 */
public record DetailBundleDTO(
        ProductSummary product,
        List<InventoryByLocation> inventoryByLocation,
        List<DailyRollupPoint> dailyRollups90d,
        List<ForecastSnapshotPoint> forecastSnapshots,
        LatestPrediction latestPrediction,
        List<RecentShipment> recentShipments,
        List<ActiveDisplay> activeDisplays
) {
    public record ProductSummary(
            UUID id,
            String sku,
            String name,
            String categoryName,
            String imageUrl,
            Integer reorderPoint,
            Integer targetStockLevel,
            Integer leadTimeDays,
            BigDecimal unitCost,
            Integer currentStock
    ) {}

    public record InventoryByLocation(
            UUID locationId,
            String locationCode,
            String storageLocationCode,
            Integer quantity
    ) {}

    public record DailyRollupPoint(
            LocalDate date,
            Integer unitsSold,
            BigDecimal revenue,
            Integer restockUnits,
            Integer damageUnits
    ) {}

    public record ForecastSnapshotPoint(
            LocalDate date,
            BigDecimal muHat,
            BigDecimal confidence,
            BigDecimal mape,
            BigDecimal daysToStockout,
            Integer currentStock
    ) {}

    public record LatestPrediction(
            Integer horizonDays,
            BigDecimal avgDailyDelta,
            BigDecimal daysToStockout,
            Integer suggestedReorderQty,
            LocalDate suggestedOrderDate,
            BigDecimal confidence,
            OffsetDateTime computedAt
    ) {}

    public record RecentShipment(
            UUID shipmentItemId,
            UUID shipmentId,
            LocalDate deliveredOn,
            Integer orderedQuantity,
            Integer receivedQuantity,
            Integer damagedQuantity,
            BigDecimal unitCost
    ) {}

    public record ActiveDisplay(
            UUID id,
            UUID locationId,
            String locationType,
            UUID machineId,
            OffsetDateTime startedAt
    ) {}
}
