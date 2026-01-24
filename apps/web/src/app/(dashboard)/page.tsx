"use client"

import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { formatDistanceStrict } from "date-fns"
import { usePermissions, Permission } from "@/hooks/use-permissions"
import { DashboardHeader } from "@/components/dashboard-header"
import { StatCards } from "@/components/dashboard/stat-cards"
import { StockLevels } from "@/components/dashboard/stock-levels"
import { RecentActivity } from "@/components/dashboard/recent-activity"
import { StockDistribution } from "@/components/dashboard/stock-distribution"
import { useDashboardStats } from "@/hooks/queries/use-dashboard-stats"
import { useShipments } from "@/hooks/queries/use-shipments"
import type { ActivityItem } from "@/types/dashboard"

export default function DashboardPage() {
  const router = useRouter()
  const { can, role } = usePermissions()
  const stats = useDashboardStats()
  const shipments = useShipments()

  // Refresh relative time strings (e.g. "5 minutes ago") even if shipment data doesn't change.
  // Updates once per minute to avoid excessive re-renders.
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(id)
  }, [])

  const activities: ActivityItem[] = useMemo(() => {
    const list = shipments.data ?? []
    return list
      .slice()
      .sort((a, b) => {
        const da = new Date(a.createdAt ?? a.orderDate).getTime()
        const db = new Date(b.createdAt ?? b.orderDate).getTime()
        return db - da
      })
      .slice(0, 5)
      .map((s) => {
        const when = new Date(s.createdAt ?? s.orderDate)
        const time = Number.isNaN(when.getTime())
          ? "Unknown"
          : formatDistanceStrict(when, now, { addSuffix: true })

        const supplierOrBlank = s.supplierName ? ` from ${s.supplierName}` : ""
        return {
          id: s.id,
          action: `Shipment ${s.shipmentNumber}${supplierOrBlank} is ${s.status}`,
          time,
        }
      })
  }, [shipments.data, now])

  // Redirect employees to storage page
  const canViewDashboard = can(Permission.DASHBOARD_VIEW)
  useEffect(() => {
    if (role && !canViewDashboard) {
      router.replace("/storage")
    }
  }, [role, canViewDashboard, router])

  // Show nothing while redirecting
  if (role && !canViewDashboard) {
    return null
  }

  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Dashboard"
        description="Overview of your inventory status"
      />
      <main className="flex-1 space-y-6 p-4 md:p-6">
        <StatCards
          isLoading={stats.isLoading}
          totalStockValue={stats.data?.totalStockValue ?? 0}
          stockValueChange={stats.data?.stockValueChange ?? 0}
          lowStockItems={stats.data?.lowStockItems ?? 0}
          criticalItems={stats.data?.criticalItems ?? 0}
          outOfStockItems={stats.data?.outOfStockItems ?? 0}
          totalSKUs={stats.data?.totalSKUs ?? 0}
        />
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <StockLevels
              items={stats.data?.stockLevels ?? []}
              isLoading={stats.isLoading}
            />
          </div>
          <div>
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
        </div>
      </main>
    </div>
  )
}
