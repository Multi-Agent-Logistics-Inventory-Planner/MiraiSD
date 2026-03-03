"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { formatDistanceToNowStrict } from "date-fns";
import { RefreshCw } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { SidebarTrigger } from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  ErrorBoundary,
  DashboardErrorFallback,
} from "@/components/error-boundary";

import { usePermissions, Permission } from "@/hooks/use-permissions";
import { useDashboardMetrics } from "@/hooks/queries/use-dashboard-metrics";
import { getShipments } from "@/lib/api/shipments";
import { ShipmentStatus, type Shipment } from "@/types/api";
import {
  useActivityFeed,
  type ActivityFeedFilters,
} from "@/hooks/queries/use-activity-feed";
import { useAllForecasts } from "@/hooks/queries/use-forecasts";
import { useProducts } from "@/hooks/queries/use-products";
import { useRealtimeDashboard } from "@/hooks/realtime";

import { ActionRequiredPanel } from "@/components/dashboard/action-required-panel";
import { RiskDistributionDonut } from "@/components/dashboard/risk-distribution-donut";
import { DemandVelocityCard } from "@/components/dashboard/demand-velocity-card";
import { UnifiedActivityFeed } from "@/components/dashboard/unified-activity-feed";
import { FilterableStockList } from "@/components/dashboard/filterable-stock-list";
import { SupplyChainStatusCard } from "@/components/dashboard/supply-chain-status-card";
import { TopReviewersCard } from "@/components/dashboard/top-reviewers-card";
import type { RiskBand, ActivityEventType } from "@/types/dashboard";

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

  // Enable real-time updates for dashboard
  useRealtimeDashboard();

  // Data hooks
  const metricsQuery = useDashboardMetrics();
  const allForecastsQuery = useAllForecasts();
  const productsQuery = useProducts();

  // Shipment queries for supply chain status card
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

  // Activity feed with filters
  const [activityFilters, setActivityFilters] = useState<ActivityFeedFilters>({
    types: ["alert", "restock", "sale", "shipment", "adjustment", "transfer"],
    showResolved: false,
  });
  const activityFeed = useActivityFeed(activityFilters);

  // Risk distribution donut filter state
  const [selectedRiskBand, setSelectedRiskBand] = useState<RiskBand | null>(
    null,
  );

  // Memoize supply chain data to avoid recalculating on every render
  const supplyChainData = useMemo(() => {
    const allActiveShipments = [
      ...(pendingShipmentsQuery.data ?? []),
      ...(inTransitShipmentsQuery.data ?? []),
    ];
    const nextShipment = getNextArrivingShipment(allActiveShipments);
    const additionalCount = allActiveShipments.length - (nextShipment ? 1 : 0);

    return {
      nextShipment,
      additionalCount,
      isLoading: pendingShipmentsQuery.isLoading || inTransitShipmentsQuery.isLoading,
    };
  }, [pendingShipmentsQuery.data, inTransitShipmentsQuery.data, pendingShipmentsQuery.isLoading, inTransitShipmentsQuery.isLoading]);

  // Sync state
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncedAt, setLastSyncedAt] = useState<Date | null>(null);

  const handleSync = useCallback(async () => {
    setIsSyncing(true);
    try {
      await queryClient.invalidateQueries();
      setLastSyncedAt(new Date());
    } finally {
      setIsSyncing(false);
    }
  }, [queryClient]);

  const lastSyncedLabel = lastSyncedAt
    ? `Last synced ${formatDistanceToNowStrict(lastSyncedAt, { addSuffix: true })}`
    : null;

  // Handle activity filter changes
  const handleActivityFilterChange = useCallback(
    (types: ActivityEventType[], showResolved: boolean) => {
      setActivityFilters({ types, showResolved });
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
        <div className="flex-1 p-4 md:p-8 space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <SidebarTrigger className="md:hidden" />
            <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
          </div>
          <div className="flex items-center gap-3">
            {lastSyncedLabel && (
              <span className="text-xs text-muted-foreground hidden sm:block">
                {lastSyncedLabel}
              </span>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={handleSync}
              disabled={isSyncing}
              className="gap-1.5"
            >
              <RefreshCw
                className={`h-3.5 w-3.5 ${isSyncing ? "animate-spin" : ""}`}
              />
              Sync
            </Button>
          </div>
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

        {/* Action Required Panel */}
        <ActionRequiredPanel
          items={metricsQuery.data?.actionRequired.items ?? []}
          isLoading={metricsQuery.isLoading}
        />

        {/* Supply Chain Status + Top Reviewers */}
        <div className="grid gap-6 md:grid-cols-2">
          <SupplyChainStatusCard
            nextShipment={supplyChainData.nextShipment}
            additionalShipmentCount={supplyChainData.additionalCount}
            isLoading={supplyChainData.isLoading}
          />
          <TopReviewersCard />
        </div>

        {/* Risk Distribution + Filterable Stock List (related - clicking risk filters list) */}
        <div className="grid lg:grid-cols-5 border bg-card/95 dark:bg-[#191919] rounded-2xl">
          <div className="lg:col-span-2">
            <RiskDistributionDonut
              data={metricsQuery.data?.riskDistribution ?? []}
              selectedSegment={selectedRiskBand}
              onSegmentClick={setSelectedRiskBand}
              isLoading={metricsQuery.isLoading}
            />
          </div>
          <div className="lg:col-span-3">
            <FilterableStockList
              forecasts={allForecastsQuery.data ?? []}
              products={productsQuery.data ?? []}
              selectedRiskBand={selectedRiskBand}
              onClearFilter={() => setSelectedRiskBand(null)}
              isLoading={allForecastsQuery.isLoading || productsQuery.isLoading}
            />
          </div>
        </div>

        {/* Demand Velocity + Activity Feed */}
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-5">
          <div className="lg:col-span-2">
            <DemandVelocityCard
              items={metricsQuery.data?.demandVelocity ?? []}
              isLoading={metricsQuery.isLoading}
            />
          </div>
          <div className="lg:col-span-3">
            <UnifiedActivityFeed
              events={activityFeed.events}
              isLoading={activityFeed.isLoading}
              hasMore={activityFeed.hasMore}
              onLoadMore={activityFeed.loadMore}
              onFilterChange={handleActivityFilterChange}
            />
          </div>
          </div>
        </div>
      </div>
    </ErrorBoundary>
  );
}
