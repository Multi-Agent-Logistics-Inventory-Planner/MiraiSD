/**
 * Dashboard Metrics Utility Functions
 *
 * Pure functions for computing dashboard metrics from raw data.
 * These functions have no side effects and can be easily tested.
 */

import { LOCATION_TYPE_LABELS } from "@/types/api";
import type { Shipment, LocationWithCounts } from "@/types/api";
import type {
  RiskDistributionSegment,
  HealthIndicator,
  RiskBand,
  TrendData,
  ShipmentPipelineData,
  LocationUtilizationData,
  StoredMetricsSnapshot,
} from "@/types/dashboard";
import { getRiskBand, RISK_BAND_COLORS, RISK_BAND_LABELS } from "@/types/dashboard";

const RISK_BAND_ORDER: RiskBand[] = ["critical", "warning", "healthy", "safe", "overstocked"];

/**
 * Computes the distribution of items across risk bands based on days to stockout.
 */
export function computeRiskDistribution(
  forecasts: { daysToStockout: number | null }[]
): RiskDistributionSegment[] {
  const counts: Record<RiskBand, number> = {
    critical: 0,
    warning: 0,
    healthy: 0,
    safe: 0,
    overstocked: 0,
  };

  for (const f of forecasts) {
    const band = getRiskBand(f.daysToStockout);
    counts[band] += 1;
  }

  const total = forecasts.length || 1;

  return RISK_BAND_ORDER.map((band) => ({
    band,
    label: RISK_BAND_LABELS[band],
    count: counts[band],
    percentage: Math.round((counts[band] / total) * 100),
    color: RISK_BAND_COLORS[band],
  }));
}

/**
 * Computes trend data by comparing current value to a previous value.
 */
export function computeTrend(
  current: number,
  previous: number | null,
  lowerIsBetter: boolean
): TrendData | null {
  if (previous === null) return null;

  const delta = current - previous;
  const deltaPercent = previous !== 0 ? (delta / previous) * 100 : null;

  let direction: "up" | "down" | "steady";
  if (Math.abs(delta) < 0.5) {
    direction = "steady";
  } else if (delta > 0) {
    direction = "up";
  } else {
    direction = "down";
  }

  // Determine if trend is favorable based on direction and metric type
  const isPositive =
    direction === "steady" ||
    (direction === "up" && !lowerIsBetter) ||
    (direction === "down" && lowerIsBetter);

  return {
    direction,
    delta: Math.abs(delta),
    deltaPercent: deltaPercent !== null ? Math.abs(deltaPercent) : null,
    periodLabel: "vs last week",
    isPositive,
  };
}

interface PerformanceMetricsInput {
  fillRate: number;
  forecastAccuracy: number;
  stockoutRate: number;
  turnoverRate: number;
}

/**
 * Computes health indicators with status and trends from performance metrics.
 */
export function computeHealthIndicators(
  metrics: PerformanceMetricsInput | null,
  previousMetrics: StoredMetricsSnapshot | null
): HealthIndicator[] {
  if (!metrics) {
    return [];
  }

  const getStatus = (
    value: number,
    target: number,
    lowerIsBetter: boolean
  ): "good" | "warning" | "critical" => {
    if (lowerIsBetter) {
      if (value <= target) return "good";
      if (value <= target * 2) return "warning";
      return "critical";
    }
    if (value >= target) return "good";
    if (value >= target * 0.7) return "warning";
    return "critical";
  };

  return [
    {
      id: "fill-rate",
      label: "Fill Rate",
      value: metrics.fillRate,
      target: 95,
      unit: "percent",
      trend: computeTrend(metrics.fillRate, previousMetrics?.fillRate ?? null, false),
      status: getStatus(metrics.fillRate, 95, false),
    },
    {
      id: "forecast-accuracy",
      label: "Forecast Accuracy",
      value: metrics.forecastAccuracy,
      target: 85,
      unit: "percent",
      trend: computeTrend(
        metrics.forecastAccuracy,
        previousMetrics?.forecastAccuracy ?? null,
        false
      ),
      status: getStatus(metrics.forecastAccuracy, 85, false),
    },
    {
      id: "stockout-rate",
      label: "Stockout Rate",
      value: metrics.stockoutRate,
      target: 5,
      unit: "percent",
      trend: computeTrend(metrics.stockoutRate, previousMetrics?.stockoutRate ?? null, true),
      status: getStatus(metrics.stockoutRate, 5, true),
    },
    {
      id: "turnover-rate",
      label: "Turnover Rate",
      value: metrics.turnoverRate,
      target: 5,
      unit: "rate",
      trend: computeTrend(metrics.turnoverRate, previousMetrics?.turnoverRate ?? null, false),
      status: getStatus(metrics.turnoverRate, 5, false),
    },
  ];
}

/**
 * Computes shipment pipeline statistics from pending and in-transit shipments.
 */
export function computeShipmentPipeline(
  pendingShipments: Shipment[],
  inTransitShipments: Shipment[]
): ShipmentPipelineData {
  const computeStats = (shipments: Shipment[]) => {
    let units = 0;
    let value = 0;

    for (const shipment of shipments) {
      for (const item of shipment.items) {
        units += item.orderedQuantity;
        value += (item.unitCost ?? 0) * item.orderedQuantity;
      }
    }

    return { count: shipments.length, units, value };
  };

  const pendingStats = computeStats(pendingShipments);
  const inTransitStats = computeStats(inTransitShipments);

  const shipmentsWithDates = inTransitShipments.filter(
    (s): s is Shipment & { expectedDeliveryDate: string } =>
      s.expectedDeliveryDate !== null && s.expectedDeliveryDate !== undefined
  );

  const nextDelivery =
    shipmentsWithDates.length > 0
      ? [...shipmentsWithDates].sort(
          (a, b) =>
            new Date(a.expectedDeliveryDate).getTime() -
            new Date(b.expectedDeliveryDate).getTime()
        )[0].expectedDeliveryDate
      : null;

  return {
    pending: pendingStats,
    inTransit: { ...inTransitStats, nextDelivery },
  };
}

/**
 * Computes location utilization statistics from location data.
 */
export function computeLocationUtilization(
  locations: LocationWithCounts[]
): LocationUtilizationData {
  let emptyLocations = 0;
  let lowStockLocations = 0;
  let wellStockedLocations = 0;

  const byTypeMap = new Map<
    string,
    { type: string; typeLabel: string; count: number; empty: number; totalUnits: number }
  >();

  for (const loc of locations) {
    if (loc.totalQuantity === 0) {
      emptyLocations++;
    } else if (loc.totalQuantity < 10) {
      lowStockLocations++;
    } else {
      wellStockedLocations++;
    }

    const typeKey = loc.locationType;
    const existing = byTypeMap.get(typeKey);

    if (existing) {
      existing.count++;
      if (loc.totalQuantity === 0) existing.empty++;
      existing.totalUnits += loc.totalQuantity;
    } else {
      byTypeMap.set(typeKey, {
        type: typeKey,
        typeLabel: LOCATION_TYPE_LABELS[loc.locationType] ?? typeKey,
        count: 1,
        empty: loc.totalQuantity === 0 ? 1 : 0,
        totalUnits: loc.totalQuantity,
      });
    }
  }

  return {
    totalLocations: locations.length,
    emptyLocations,
    lowStockLocations,
    wellStockedLocations,
    byType: Array.from(byTypeMap.values()).sort((a, b) => b.totalUnits - a.totalUnits),
  };
}
