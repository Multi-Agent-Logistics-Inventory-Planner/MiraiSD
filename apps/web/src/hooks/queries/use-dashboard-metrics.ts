"use client";

/**
 * Dashboard Metrics Hook
 *
 * Aggregates data from multiple sources to provide comprehensive dashboard metrics.
 * Uses extracted utility functions for computations and localStorage for trend data.
 */

import { useMemo, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAllForecasts, useAtRiskForecasts } from "./use-forecasts";
import { useNotifications, useNotificationCounts } from "./use-notifications";
import { useProducts } from "./use-products";
import { getPerformanceMetrics } from "@/lib/api/analytics";
import { getShipments } from "@/lib/api/shipments";
import { getLocationsWithCounts } from "@/lib/api/locations";
import { getAuditLog } from "@/lib/api/stock-movements";
import { ShipmentStatus, NotificationSeverity } from "@/types/api";
import type { Product } from "@/types/api";
import type {
  DashboardMetrics,
  ActionRequiredItem,
  QuickStats,
  StoredMetricsSnapshot,
} from "@/types/dashboard";

import {
  computeRiskDistribution,
  computeHealthIndicators,
  computeDemandVelocity,
  computeShipmentPipeline,
  computeLocationUtilization,
} from "@/lib/utils/dashboard-metrics-utils";
import {
  getPreviousMetrics,
  storeCurrentMetrics,
} from "@/lib/utils/dashboard-metrics-storage";

export function useDashboardMetrics() {
  // Use lazy initialization to load from localStorage synchronously on mount
  const [previousMetrics] = useState<StoredMetricsSnapshot | null>(() => getPreviousMetrics());

  // Data source hooks
  const allForecastsQuery = useAllForecasts();
  const atRiskQuery = useAtRiskForecasts(7);
  const notificationCountsQuery = useNotificationCounts();
  const notificationsQuery = useNotifications();
  const productsQuery = useProducts();

  // Additional API queries
  const performanceMetricsQuery = useQuery({
    queryKey: ["analytics", "performance-metrics"],
    queryFn: getPerformanceMetrics,
    staleTime: 5 * 60 * 1000,
  });

  const pendingShipmentsQuery = useQuery({
    queryKey: ["shipments", "pending"],
    queryFn: () => getShipments(ShipmentStatus.PENDING),
    staleTime: 60 * 1000,
  });

  const inTransitShipmentsQuery = useQuery({
    queryKey: ["shipments", "in-transit"],
    queryFn: () => getShipments(ShipmentStatus.IN_TRANSIT),
    staleTime: 60 * 1000,
  });

  const locationsQuery = useQuery({
    queryKey: ["locations", "with-counts"],
    queryFn: () => getLocationsWithCounts(),
    staleTime: 60 * 1000,
  });

  const auditLogQuery = useQuery({
    queryKey: ["audit-log", "dashboard-14d"],
    queryFn: async () => {
      const now = new Date();
      const twoWeeksAgo = new Date(now);
      twoWeeksAgo.setDate(twoWeeksAgo.getDate() - 14);

      const result = await getAuditLog(
        { fromDate: twoWeeksAgo.toISOString().split("T")[0] },
        0,
        500
      );
      return result.content;
    },
    staleTime: 5 * 60 * 1000,
  });

  // Store metrics for future trend calculations
  useEffect(() => {
    if (performanceMetricsQuery.data) {
      storeCurrentMetrics(performanceMetricsQuery.data);
    }
  }, [performanceMetricsQuery.data]);

  // Aggregate loading state
  const isLoading =
    allForecastsQuery.isLoading ||
    atRiskQuery.isLoading ||
    notificationCountsQuery.isLoading ||
    notificationsQuery.isLoading ||
    productsQuery.isLoading ||
    performanceMetricsQuery.isLoading ||
    pendingShipmentsQuery.isLoading ||
    inTransitShipmentsQuery.isLoading ||
    locationsQuery.isLoading ||
    auditLogQuery.isLoading;

  // First error from any query
  const error =
    allForecastsQuery.error ??
    atRiskQuery.error ??
    notificationCountsQuery.error ??
    notificationsQuery.error ??
    productsQuery.error ??
    performanceMetricsQuery.error ??
    pendingShipmentsQuery.error ??
    inTransitShipmentsQuery.error ??
    locationsQuery.error ??
    auditLogQuery.error;

  // Compute aggregated dashboard metrics
  const data: DashboardMetrics | null = useMemo(() => {
    const allForecasts = allForecastsQuery.data;
    const atRiskForecasts = atRiskQuery.data;
    const notificationCounts = notificationCountsQuery.data;
    const notifications = notificationsQuery.data;
    const products = productsQuery.data;
    const performanceMetrics = performanceMetricsQuery.data;
    const pendingShipments = pendingShipmentsQuery.data;
    const inTransitShipments = inTransitShipmentsQuery.data;
    const locations = locationsQuery.data;
    const auditLogs = auditLogQuery.data;

    // Require minimum data to render
    if (!allForecasts || !products) {
      return null;
    }

    // Build product lookup map
    const productMap = new Map<string, Product>();
    for (const p of products) {
      productMap.set(p.id, p);
    }

    // Compute action required items
    const actionRequiredItems: ActionRequiredItem[] = (atRiskForecasts ?? []).map((f) => {
      const product = productMap.get(f.itemId);
      const leadTimeDays = product?.leadTimeDays ?? null;
      const isLeadTimeExceeded = leadTimeDays !== null && f.daysToStockout < leadTimeDays;

      return {
        itemId: f.itemId,
        itemName: f.itemName,
        itemSku: f.itemSku,
        imageUrl: product?.imageUrl ?? null,
        daysToStockout: f.daysToStockout,
        currentStock: f.currentStock,
        dailyDemand: Math.abs(f.avgDailyDelta),
        suggestedReorderQty: f.suggestedReorderQty,
        unitCost: f.unitCost ?? null,
        reorderCost: f.unitCost ? f.suggestedReorderQty * f.unitCost : null,
        leadTimeDays,
        isLeadTimeExceeded,
        targetStockLevel: product?.targetStockLevel ?? null,
        reorderPoint: product?.reorderPoint ?? null,
      };
    });

    const totalReorderCost = actionRequiredItems.reduce(
      (sum, item) => sum + (item.reorderCost ?? 0),
      0
    );

    // Compute derived metrics using utility functions
    const riskDistribution = computeRiskDistribution(allForecasts);
    const healthIndicators = computeHealthIndicators(performanceMetrics ?? null, previousMetrics);
    const demandVelocity = computeDemandVelocity(allForecasts, auditLogs ?? []);

    // Compute stock value
    const stockValue = products.reduce((sum, p) => {
      const qty = p.quantity ?? 0;
      const cost = p.unitCost ?? 0;
      return sum + qty * cost;
    }, 0);

    // Count alerts by severity
    const alertsBySeverity = { critical: 0, warning: 0, info: 0 };
    if (notifications) {
      for (const n of notifications) {
        if (n.resolvedAt) continue;
        if (n.severity === NotificationSeverity.CRITICAL) alertsBySeverity.critical++;
        else if (n.severity === NotificationSeverity.WARNING) alertsBySeverity.warning++;
        else if (n.severity === NotificationSeverity.INFO) alertsBySeverity.info++;
      }
    }

    const quickStats: QuickStats = {
      totalSkus: products.length,
      stockValue,
      activeAlerts: notificationCounts?.active ?? 0,
      pendingShipments: pendingShipments?.length ?? 0,
      alertsBySeverity,
    };

    const shipmentPipeline =
      pendingShipments && inTransitShipments
        ? computeShipmentPipeline(pendingShipments, inTransitShipments)
        : null;

    const locationUtilization = locations ? computeLocationUtilization(locations) : null;

    return {
      actionRequired: {
        items: actionRequiredItems,
        totalItems: actionRequiredItems.length,
        totalReorderCost,
      },
      riskDistribution,
      healthIndicators,
      demandVelocity,
      quickStats,
      shipmentPipeline,
      locationUtilization,
    };
  }, [
    allForecastsQuery.data,
    atRiskQuery.data,
    notificationCountsQuery.data,
    notificationsQuery.data,
    productsQuery.data,
    performanceMetricsQuery.data,
    pendingShipmentsQuery.data,
    inTransitShipmentsQuery.data,
    locationsQuery.data,
    auditLogQuery.data,
    previousMetrics,
  ]);

  return {
    data,
    isLoading,
    error: error as Error | null,
  };
}
