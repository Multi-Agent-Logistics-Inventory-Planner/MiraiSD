"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";

import { SidebarTrigger } from "@/components/ui/sidebar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  ErrorBoundary,
  DashboardErrorFallback,
} from "@/components/error-boundary";

import { usePermissions, Permission } from "@/hooks/use-permissions";
import {
  useActivityFeed,
  type ActivityFeedFilters,
} from "@/hooks/queries/use-activity-feed";
import { UnifiedActivityFeed } from "@/components/dashboard/unified-activity-feed";
import {
  ConnectedOrdersCard,
  ConnectedStockStatusCard,
  ConnectedHighestDemandCard,
  ConnectedSupplyChainStatusCard,
} from "@/components/dashboard/connected-cards";
import { TopReviewersCard } from "@/components/dashboard/top-reviewers-card";
import type { ActivityEventType } from "@/types/dashboard";

export default function DashboardPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { can, role } = usePermissions();

  // Activity feed with filters (kept at page level for filter state management)
  const [activityFilters, setActivityFilters] = useState<ActivityFeedFilters>({
    types: ["alert", "restock", "sale", "shipment", "adjustment", "transfer"],
    showResolved: false,
  });
  const activityFeed = useActivityFeed(activityFilters);

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

  const hasError = !!activityFeed.error;

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
          <SidebarTrigger />
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
        {/* Each card fetches its own data for progressive loading */}
        <div className="grid grid-cols-1 gap-4 sm:gap-6 md:grid-cols-3 md:grid-rows-2">
          {/* Orders - mobile order 1, desktop row 1 col 1 */}
          <div className="order-1">
            <ConnectedOrdersCard />
          </div>
          {/* Stock - mobile order 3, desktop row 1 col 2 */}
          <div className="order-3 md:order-2">
            <ConnectedStockStatusCard />
          </div>
          {/* High Demand - mobile order 5 (last), desktop row-span-2 col 3 */}
          <div className="order-5 md:order-3 md:row-span-2 h-auto md:h-full">
            <ConnectedHighestDemandCard />
          </div>
          {/* Incoming Shipments - mobile order 2, desktop row 2 col 1 */}
          <div className="order-2 md:order-4">
            <ConnectedSupplyChainStatusCard />
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
