import { DashboardHeader } from "@/components/dashboard-header"
import { StatCards } from "@/components/dashboard/stat-cards"
import { StockLevels } from "@/components/dashboard/stock-levels"
import { RecentActivity } from "@/components/dashboard/recent-activity"
import { StockDistribution } from "@/components/dashboard/stock-distribution"
import {
  dashboardStats,
  inventoryItems,
  recentActivity,
  categoryDistribution,
} from "@/lib/data"

export default function DashboardPage() {
  return (
    <div className="flex flex-col">
      <DashboardHeader
        title="Dashboard"
        description="Overview of your inventory status"
      />
      <main className="flex-1 space-y-6 p-4 md:p-6">
        <StatCards
          totalStockValue={dashboardStats.totalStockValue}
          stockValueChange={dashboardStats.stockValueChange}
          lowStockItems={dashboardStats.lowStockItems}
          criticalItems={dashboardStats.criticalItems}
          outOfStockItems={dashboardStats.outOfStockItems}
          totalSKUs={dashboardStats.totalSKUs}
        />
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <StockLevels items={inventoryItems} />
          </div>
          <div>
            <RecentActivity activities={recentActivity} />
          </div>
        </div>
        <div className="grid gap-6 md:grid-cols-2">
          <StockDistribution data={categoryDistribution} />
        </div>
      </main>
    </div>
  )
}
