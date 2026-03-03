/**
 * Dashboard Types
 *
 * Type definitions for the urgency-first dashboard
 */

import type { NotificationSeverity } from "./api";

// Legacy types (used by use-dashboard-stats.ts)
export type StockStatus = "good" | "low" | "critical" | "out-of-stock";

export interface StockLevelItem {
  itemId: string;
  sku: string;
  name: string;
  stock: number;
  maxStock: number;
  status: StockStatus;
  lastUpdated: string;
  daysToStockout?: number;
}

export interface CategoryData {
  name: string;
  value: number;
  fill: string;
}

export interface ActivityItem {
  id: string;
  action: string;
  time: string;
}

export interface DashboardStats {
  totalStockValue: number;
  stockValueChange: number | null;
  lowStockItems: number;
  criticalItems: number;
  outOfStockItems: number;
  totalSKUs: number;
  stockLevels: StockLevelItem[];
  categoryDistribution: CategoryData[];
}

// Risk band definitions based on days to stockout
export type RiskBand = "critical" | "warning" | "healthy" | "safe" | "overstocked";

export const RISK_BAND_THRESHOLDS = {
  critical: 3,   // <= 3 days
  warning: 7,    // 4-7 days
  healthy: 30,   // 8-30 days
  safe: 60,      // 31-60 days
  // overstocked: > 60 days
} as const;

// Urgency level definitions for action required panel
export type UrgencyLevel = "critical" | "urgent" | "attention";

export const URGENCY_THRESHOLDS = {
  critical: 1,   // <= 1 day
  urgent: 3,     // 2-3 days
  attention: 7,  // 4-7 days
} as const;

export const RISK_BAND_COLORS: Record<RiskBand, string> = {
  critical: "#ef4444",    // red-500
  warning: "#f59e0b",     // amber-500
  healthy: "#22c55e",     // green-500
  safe: "#3b82f6",        // blue-500
  overstocked: "#8b5cf6", // violet-500
};

export const RISK_BAND_LABELS: Record<RiskBand, string> = {
  critical: "Critical",
  warning: "Warning",
  healthy: "Healthy",
  safe: "Safe",
  overstocked: "Overstocked",
};

// Action required item - items that need reordering soon
export interface ActionRequiredItem {
  itemId: string;
  itemName: string;
  itemSku: string;
  imageUrl: string | null;
  daysToStockout: number;
  currentStock: number;
  dailyDemand: number;
  suggestedReorderQty: number;
  unitCost: number | null;
  reorderCost: number | null;
  leadTimeDays: number | null;
  isLeadTimeExceeded: boolean;
  targetStockLevel: number | null;
  reorderPoint: number | null;
}

// Risk distribution data for donut chart
export interface RiskDistributionSegment {
  band: RiskBand;
  label: string;
  count: number;
  percentage: number;
  color: string;
}

// Health indicator KPI with trend
export interface HealthIndicator {
  id: string;
  label: string;
  value: number;
  target: number;
  unit: "percent" | "rate" | "count";
  trend: TrendData | null;
  status: "good" | "warning" | "critical";
}

export interface TrendData {
  direction: "up" | "down" | "steady";
  delta: number;
  deltaPercent: number | null;
  periodLabel: string;
  isPositive: boolean; // Whether the trend direction is favorable (considers lowerIsBetter)
}

// Performance metrics with trends (extends API type)
export interface PerformanceMetricsWithTrends {
  fillRate: number;
  fillRateTrend: TrendData | null;
  forecastAccuracy: number;
  forecastAccuracyTrend: TrendData | null;
  stockoutRate: number;
  stockoutRateTrend: TrendData | null;
  turnoverRate: number;
  turnoverRateTrend: TrendData | null;
}

// Demand velocity item (top movers)
export interface DemandVelocityItem {
  itemId: string;
  itemName: string;
  itemSku: string;
  currentDelta: number;
  previousDelta: number;
  changePercent: number;
  changeDirection: "increase" | "decrease";
  sparklineData: number[];
}

// Unified activity feed event types
export type ActivityEventType = "alert" | "restock" | "sale" | "shipment" | "adjustment" | "transfer";

export interface ActivityFeedEvent {
  id: string;
  type: ActivityEventType;
  title: string;
  description: string | null;
  timestamp: string;
  severity: NotificationSeverity | null;
  metadata: {
    itemId?: string;
    itemName?: string;
    itemSku?: string;
    quantity?: number;
    notificationId?: string;
    shipmentId?: string;
    shipmentNumber?: string;
    resolved?: boolean;
  };
}

// Quick stats bar data
export interface QuickStats {
  totalSkus: number;
  stockValue: number;
  activeAlerts: number;
  pendingShipments: number;
  alertsBySeverity: {
    critical: number;
    warning: number;
    info: number;
  };
}

// Filterable stock list item
export interface FilterableStockItem {
  itemId: string;
  itemName: string;
  itemSku: string;
  imageUrl: string | null;
  status: RiskBand;
  daysToStockout: number | null;
  currentStock: number;
  maxStock: number;
  stockPercentage: number;
  lastUpdated: string;
}

// Shipment pipeline data for incoming shipments card
export interface ShipmentPipelineData {
  pending: { count: number; units: number; value: number };
  inTransit: { count: number; units: number; value: number; nextDelivery: string | null };
}

// Location utilization data for storage overview card
export interface LocationUtilizationData {
  totalLocations: number;
  emptyLocations: number;
  lowStockLocations: number;
  wellStockedLocations: number;
  byType: Array<{
    type: string;
    typeLabel: string;
    count: number;
    empty: number;
    totalUnits: number;
  }>;
}

// Stored metrics snapshot for localStorage-based trends
export interface StoredMetricsSnapshot {
  timestamp: string;
  fillRate: number;
  forecastAccuracy: number;
  stockoutRate: number;
  turnoverRate: number;
}

// Dashboard metrics aggregated data
export interface DashboardMetrics {
  actionRequired: {
    items: ActionRequiredItem[];
    totalItems: number;
    totalReorderCost: number;
  };
  riskDistribution: RiskDistributionSegment[];
  healthIndicators: HealthIndicator[];
  demandVelocity: DemandVelocityItem[];
  quickStats: QuickStats;
  shipmentPipeline: ShipmentPipelineData | null;
  locationUtilization: LocationUtilizationData | null;
}

// Hook return types
export interface UseDashboardMetricsResult {
  data: DashboardMetrics | null;
  isLoading: boolean;
  error: Error | null;
}

export interface UseActivityFeedResult {
  events: ActivityFeedEvent[];
  isLoading: boolean;
  error: Error | null;
  hasMore: boolean;
  loadMore: () => void;
}

// Filter state for stock list
export interface StockListFilters {
  riskBand: RiskBand | null;
  searchQuery: string;
  sortBy: "daysToStockout" | "name" | "stock" | "status";
  sortDirection: "asc" | "desc";
  page: number;
  pageSize: number;
}

// Utility function to get risk band from days to stockout
export function getRiskBand(daysToStockout: number | null | undefined): RiskBand {
  if (daysToStockout === null || daysToStockout === undefined) {
    return "safe";
  }
  if (daysToStockout <= RISK_BAND_THRESHOLDS.critical) {
    return "critical";
  }
  if (daysToStockout <= RISK_BAND_THRESHOLDS.warning) {
    return "warning";
  }
  if (daysToStockout <= RISK_BAND_THRESHOLDS.healthy) {
    return "healthy";
  }
  if (daysToStockout <= RISK_BAND_THRESHOLDS.safe) {
    return "safe";
  }
  return "overstocked";
}

// Utility function to get urgency level from days to stockout
export function getUrgencyLevel(daysToStockout: number): UrgencyLevel {
  if (daysToStockout <= URGENCY_THRESHOLDS.critical) {
    return "critical";
  }
  if (daysToStockout <= URGENCY_THRESHOLDS.urgent) {
    return "urgent";
  }
  return "attention";
}

// Utility to format trend as display string
export function formatTrendDelta(trend: TrendData | null): string {
  if (!trend) return "";
  const sign = trend.direction === "up" ? "+" : trend.direction === "down" ? "-" : "";
  if (trend.deltaPercent !== null) {
    return `${sign}${Math.abs(trend.deltaPercent).toFixed(1)}%`;
  }
  return `${sign}${Math.abs(trend.delta).toFixed(1)}`;
}
