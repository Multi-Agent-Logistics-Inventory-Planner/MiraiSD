"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { SidebarTrigger } from "@/components/ui/sidebar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  ErrorBoundary,
  DashboardErrorFallback,
} from "@/components/error-boundary";

import { usePermissions, Permission } from "@/hooks/use-permissions";
import { useDashboardMetrics } from "@/hooks/queries/use-dashboard-metrics";
import { useShipmentDisplayStatusCounts } from "@/hooks/queries/use-shipments";
import { getShipmentsPaged } from "@/lib/api/shipments";
import { ShipmentStatus } from "@/types/api";
import {
  useActivityFeed,
  type ActivityFeedFilters,
} from "@/hooks/queries/use-activity-feed";
import { useAllForecasts } from "@/hooks/queries/use-forecasts";
import { useProducts } from "@/hooks/queries/use-products";
import { UnifiedActivityFeed } from "@/components/dashboard/unified-activity-feed";
import { StockStatusCard } from "@/components/dashboard/stock-status-card";
import { OrdersCard } from "@/components/dashboard/orders-card";
import { HighestDemandCard } from "@/components/dashboard/highest-demand-card";
import { SupplyChainStatusCard } from "@/components/dashboard/supply-chain-status-card";
import { TopReviewersCard } from "@/components/dashboard/top-reviewers-card";
import type { Shipment } from "@/types/api";
import type { ActivityEventType } from "@/types/dashboard";

function getNextArrivingShipment(shipments: Shipment[]): Shipment | null {
  const withDates = shipments.filter(
    (s): s is Shipment & { expectedDeliveryDate: string } =>
      s.expectedDeliveryDate !== null && s.expectedDeliveryDate !== undefined
  );
  if (withDates.length === 0) return null;

  return withDates.sort(
    (a, b) =>
      new Date(a.expectedDeliveryDate).getTime() -
      new Date(b.expectedDeliveryDate).getTime()
  )[0];
}

export default function DashboardPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { can, role } = usePermissions();

  // Data hooks
  const metricsQuery = useDashboardMetrics();
  const allForecastsQuery = useAllForecasts();
  const productsQuery = useProducts();

  // Get shipment display status counts (matching shipments page)
  const statusCountsQuery = useShipmentDisplayStatusCounts();

  // Fetch shipments to calculate overdue count
  const shipmentsQuery = useQuery({
    queryKey: ["shipments", "all-recent"],
    queryFn: async () => {
      const response = await getShipmentsPaged({}, 0, 200);
      return response.content;
    },
    staleTime: 60 * 1000,
  });

  // Activity feed with filters
  const [activityFilters, setActivityFilters] = useState<ActivityFeedFilters>({
    types: ["alert", "restock", "sale", "shipment", "adjustment", "transfer"],
    showResolved: false,
  });
  const activityFeed = useActivityFeed(activityFilters);

  // Calculate overdue count from shipments
  const overdueCount = useMemo(() => {
    const shipments = shipmentsQuery.data ?? [];
    const now = new Date();

    return shipments.filter((s) => {
      if (!s.expectedDeliveryDate) return false;
      return (
        new Date(s.expectedDeliveryDate) < now &&
        s.status !== ShipmentStatus.DELIVERED &&
        s.status !== ShipmentStatus.CANCELLED
      );
    }).length;
  }, [shipmentsQuery.data]);

  // Orders metrics combining display status counts with overdue
  const ordersMetrics = {
    overdue: overdueCount,
    active: statusCountsQuery.data?.ACTIVE ?? 0,
    partial: statusCountsQuery.data?.PARTIAL ?? 0,
    completed: statusCountsQuery.data?.COMPLETED ?? 0,
  };

  // Supply chain data for incoming shipments card
  const supplyChainData = useMemo(() => {
    const shipments = shipmentsQuery.data ?? [];
    const activeShipments = shipments.filter(
      (s) => s.status === ShipmentStatus.PENDING || s.status === ShipmentStatus.IN_TRANSIT
    );
    const nextShipment = getNextArrivingShipment(activeShipments);
    const additionalCount = activeShipments.length - (nextShipment ? 1 : 0);

    return {
      nextShipment,
      additionalCount,
      isLoading: shipmentsQuery.isLoading,
    };
  }, [shipmentsQuery.data, shipmentsQuery.isLoading]);

  // Find the highest demand product from forecasts
  const highestDemandProduct = useMemo(() => {
    const forecasts = allForecastsQuery.data ?? [];
    const consuming = forecasts.filter((f) => f.avgDailyDelta < 0);
    if (!consuming.length) return null;

    const sorted = [...consuming].sort((a, b) => a.avgDailyDelta - b.avgDailyDelta);
    const top = sorted[0];
    const product = productsQuery.data?.find((p) => p.id === top.itemId);

    return {
      ...top,
      imageUrl: product?.imageUrl ?? null,
    };
  }, [allForecastsQuery.data, productsQuery.data]);

  // Handle activity filter changes
  const handleActivityFilterChange = useCallback(
    (types: ActivityEventType[]) => {
      setActivityFilters((prev) => ({ ...prev, types }));
    },
    [],
  );

  // Redirect employees to storage page
  const canViewDashboard = can(Permission.DASHBOARD_VIEW);
  useEffect(() => {
    if (role && !canViewDashboard) {
      router.replace("/storage");
    }
  }, [role, canViewDashboard, router]);

  if (role && !canViewDashboard) return null;

  const hasError = !!metricsQuery.error || !!activityFeed.error;

  function handleRetry() {
    queryClient.invalidateQueries();
  }

  return (
    <ErrorBoundary
      fallback={<DashboardErrorFallback onRetry={handleRetry} />}
      onReset={handleRetry}
    >
      <div className="flex flex-col min-h-screen">
        <div className="flex-1 p-4 md:p-8 space-y-4 sm:space-y-6">
        {/* Header */}
        <div className="flex items-center gap-2">
          <SidebarTrigger className="md:hidden" />
          <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        </div>

        {/* Error Alert */}
        {hasError && (
          <Alert variant="destructive">
            <AlertDescription className="flex items-center justify-between">
              <span>Some data failed to load.</span>
              <button
                onClick={handleRetry}
                className="text-xs underline-offset-2 hover:underline ml-4"
              >
                Retry
              </button>
            </AlertDescription>
          </Alert>
        )}

        {/* Main Cards Grid - 3 columns with right column spanning 2 rows */}
        <div className="grid gap-4 sm:gap-6 md:grid-cols-3 md:grid-rows-2">
          {/* Orders - mobile order 1, desktop row 1 col 1 */}
          <div className="order-1">
            <OrdersCard
              overdue={ordersMetrics.overdue}
              active={ordersMetrics.active}
              partial={ordersMetrics.partial}
              completed={ordersMetrics.completed}
              isLoading={shipmentsQuery.isLoading || statusCountsQuery.isLoading}
            />
          </div>
          {/* Stock - mobile order 3, desktop row 1 col 2 */}
          <div className="order-3 md:order-2">
            <StockStatusCard
              products={productsQuery.data ?? []}
              isLoading={productsQuery.isLoading}
            />
          </div>
          {/* High Demand - mobile order 5 (last), desktop row-span-2 col 3 */}
          <div className="order-5 md:order-3 md:row-span-2 h-auto md:h-full">
            <HighestDemandCard
              product={highestDemandProduct}
              isLoading={allForecastsQuery.isLoading || productsQuery.isLoading}
            />
          </div>
          {/* Incoming Shipments - mobile order 2, desktop row 2 col 1 */}
          <div className="order-2 md:order-4">
            <SupplyChainStatusCard
              nextShipment={supplyChainData.nextShipment}
              additionalShipmentCount={supplyChainData.additionalCount}
              isLoading={supplyChainData.isLoading}
            />
          </div>
          {/* Top Reviewers - mobile order 4, desktop row 2 col 2 */}
          <div className="order-4 md:order-5">
            <TopReviewersCard />
          </div>
        </div>

        {/* Activity Feed */}
        <UnifiedActivityFeed
          events={activityFeed.events}
          isLoading={activityFeed.isLoading}
          hasMore={activityFeed.hasMore}
          onLoadMore={activityFeed.loadMore}
          onFilterChange={handleActivityFilterChange}
        />
        </div>
      </div>
    </ErrorBoundary>
  );
}
