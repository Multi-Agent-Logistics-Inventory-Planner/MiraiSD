"use client"

import { SidebarTrigger } from "@/components/ui/sidebar"
import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { formatDistanceStrict, formatDistanceToNowStrict, isThisMonth } from "date-fns"
import { RefreshCw } from "lucide-react"
import { useQueryClient } from "@tanstack/react-query"
import { usePermissions, Permission } from "@/hooks/use-permissions"
import { StatCards } from "@/components/dashboard/stat-cards"
import { StockLevels } from "@/components/dashboard/stock-levels"
import { RecentActivity } from "@/components/dashboard/recent-activity"
import { StockDistribution } from "@/components/dashboard/stock-distribution"
import { FastestSelling } from "@/components/dashboard/fastest-selling"
import { useDashboardStats } from "@/hooks/queries/use-dashboard-stats"
import { useShipments } from "@/hooks/queries/use-shipments"
import { useAtRiskForecasts, useAllForecasts } from "@/hooks/queries/use-forecasts"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription } from "@/components/ui/alert"
import type { ActivityItem } from "@/types/dashboard"

export default function DashboardPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { can, role } = usePermissions()

  const forecastsQuery = useAtRiskForecasts(30)
  const allForecastsQuery = useAllForecasts()
  const forecastByItemId = useMemo(() => {
    const map: Record<string, number> = {}
    for (const f of forecastsQuery.data ?? []) {
      map[f.itemId] = f.daysToStockout
    }
    return map
  }, [forecastsQuery.data])

  const stats = useDashboardStats({ forecastByItemId })
  const shipments = useShipments()

  // Refresh relative time strings — updates once per minute
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(id)
  }, [])

  // Force sync state
  const [isSyncing, setIsSyncing] = useState(false)
  const [lastSyncedAt, setLastSyncedAt] = useState<Date | null>(null)

  async function handleSync() {
    setIsSyncing(true)
    try {
      await queryClient.invalidateQueries()
      setLastSyncedAt(new Date())
    } finally {
      setIsSyncing(false)
    }
  }

  const lastSyncedLabel = lastSyncedAt
    ? `Last synced ${formatDistanceToNowStrict(lastSyncedAt, { addSuffix: true })}`
    : null

  // Current-month shipment activities
  const activities: ActivityItem[] = useMemo(() => {
    const list = shipments.data?.content ?? []
    return list
      .filter((s) => {
        const when = new Date(s.createdAt ?? s.orderDate)
        return !Number.isNaN(when.getTime()) && isThisMonth(when)
      })
      .slice()
      .sort((a, b) => {
        const da = new Date(a.createdAt ?? a.orderDate).getTime()
        const db = new Date(b.createdAt ?? b.orderDate).getTime()
        return db - da
      })
      .map((s) => {
        const when = new Date(s.createdAt ?? s.orderDate)
        const time = formatDistanceStrict(when, now, { addSuffix: true })
        const supplierOrBlank = s.supplierName ? ` from ${s.supplierName}` : ""
        return {
          id: s.id,
          action: `Shipment ${s.shipmentNumber}${supplierOrBlank} is ${s.status}`,
          time,
        }
      })
  }, [shipments.data?.content, now])

  // Redirect employees to storage page
  const canViewDashboard = can(Permission.DASHBOARD_VIEW)
  useEffect(() => {
    if (role && !canViewDashboard) {
      router.replace("/storage")
    }
  }, [role, canViewDashboard, router])

  if (role && !canViewDashboard) return null

  const hasError = !!stats.error || !!shipments.error

  function handleRetry() {
    queryClient.invalidateQueries()
  }

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
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
            <RefreshCw className={`h-3.5 w-3.5 ${isSyncing ? "animate-spin" : ""}`} />
            Sync
          </Button>
        </div>
      </div>

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

      <div className="flex-1 space-y-6">
        <StatCards
          isLoading={stats.isLoading}
          isError={!!stats.error}
          onRetry={handleRetry}
          totalStockValue={stats.data?.totalStockValue ?? 0}
          stockValueChange={stats.data?.stockValueChange ?? null}
          lowStockItems={stats.data?.lowStockItems ?? 0}
          criticalItems={stats.data?.criticalItems ?? 0}
          outOfStockItems={stats.data?.outOfStockItems ?? 0}
          totalSKUs={stats.data?.totalSKUs ?? 0}
        />
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <div className="lg:col-span-2 h-[520px]">
            <StockLevels
              items={stats.data?.stockLevels ?? []}
              isLoading={stats.isLoading}
            />
          </div>
          <div className="h-[520px]">
            <RecentActivity
              activities={activities}
              isLoading={shipments.isLoading}
            />
          </div>
        </div>
        <div className="grid gap-6 md:grid-cols-2">
          <StockDistribution
            data={stats.data?.categoryDistribution ?? []}
            isLoading={stats.isLoading}
          />
          <FastestSelling
            forecasts={allForecastsQuery.data ?? []}
            isLoading={allForecastsQuery.isLoading}
          />
        </div>
      </div>
    </div>
  )
}
