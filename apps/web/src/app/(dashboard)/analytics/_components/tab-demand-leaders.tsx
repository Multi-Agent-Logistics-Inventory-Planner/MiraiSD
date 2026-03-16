"use client"

import { useState } from "react"
import {
  Activity,
  AlertCircle,
  Award,
  BarChart3,
  Package,
  Target,
  TrendingDown,
  TrendingUp,
  Trophy,
  Zap,
} from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs"
import { cn } from "@/lib/utils"
import { useDemandLeaders } from "@/hooks/queries/use-demand-leaders"
import type {
  CategoryRanking,
  DemandLeader,
  DemandLeadersPeriod,
  DemandSummary,
} from "@/types/analytics"

const PERIOD_OPTIONS: { value: DemandLeadersPeriod; label: string }[] = [
  { value: "7d", label: "Last 7 Days" },
  { value: "30d", label: "Last 30 Days" },
  { value: "90d", label: "Last 90 Days" },
  { value: "ytd", label: "Year to Date" },
]

function formatNumber(value: number | null, decimals: number = 1): string {
  if (value === null || value === undefined) return 'N/A'
  return value.toFixed(decimals)
}

function getAccuracyColor(accuracy: number | null): string {
  if (accuracy === null) return 'text-muted-foreground'
  if (accuracy >= 80) return 'text-green-600'
  if (accuracy >= 60) return 'text-yellow-600'
  return 'text-red-600'
}

function SummaryCards({
  summary,
  isLoading,
}: {
  summary?: DemandSummary
  isLoading: boolean
}) {
  if (isLoading) {
    return (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={i}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-4 w-4" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-8 w-20" />
              <Skeleton className="mt-2 h-3 w-32" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  const changeIsPositive = (summary?.demandGrowthPercent ?? 0) >= 0

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Total Demand Velocity</CardTitle>
          <Activity className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            {formatNumber(summary?.totalDemandVelocity ?? 0, 2)}
            <span className="text-sm font-normal text-muted-foreground ml-1">units/day</span>
          </div>
          <div className="flex items-center gap-1 mt-1">
            {changeIsPositive ? (
              <TrendingUp className="h-3 w-3 text-green-500" />
            ) : (
              <TrendingDown className="h-3 w-3 text-red-500" />
            )}
            <span
              className={cn(
                "text-xs",
                changeIsPositive ? "text-green-600" : "text-red-600"
              )}
            >
              {changeIsPositive ? "+" : ""}
              {formatNumber(summary?.demandGrowthPercent ?? 0)}% vs previous period
            </span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Total Period Demand</CardTitle>
          <Package className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            {(summary?.totalPeriodDemand ?? 0).toLocaleString()}
            <span className="text-sm font-normal text-muted-foreground ml-1">units</span>
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            {summary?.periodLabel}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Active Products</CardTitle>
          <BarChart3 className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            {summary?.uniqueItemsWithDemand ?? 0}
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            Products with demand
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">System Accuracy</CardTitle>
          <Target className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className={cn("text-2xl font-bold", getAccuracyColor(summary?.systemForecastAccuracy ?? null))}>
            {formatNumber(summary?.systemForecastAccuracy ?? 0)}%
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            Average forecast accuracy
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

function DemandLeaderRow({ leader, maxValue, metricType }: {
  leader: DemandLeader
  maxValue: number
  metricType: 'velocity' | 'stock'
}) {
  const value = metricType === 'velocity' ? leader.demandVelocity : leader.stockVelocity
  const percentage = maxValue > 0 ? (value / maxValue) * 100 : 0

  return (
    <div className="flex items-center gap-4 p-3 rounded-lg hover:bg-muted/50 transition-colors">
      <div className="flex-shrink-0 w-8 text-center">
        {leader.rank <= 3 ? (
          <Trophy
            className={cn(
              "h-5 w-5 mx-auto",
              leader.rank === 1
                ? "text-yellow-500"
                : leader.rank === 2
                ? "text-gray-400"
                : "text-amber-600"
            )}
          />
        ) : (
          <span className="text-sm font-medium text-muted-foreground">
            #{leader.rank}
          </span>
        )}
      </div>

      <div className="flex-shrink-0 h-12 w-12">
        {leader.imageUrl ? (
          <img
            src={leader.imageUrl}
            alt={leader.name}
            className="h-12 w-12 rounded-md object-cover"
          />
        ) : (
          <div className="h-12 w-12 rounded-md bg-muted flex items-center justify-center">
            <Package className="h-6 w-6 text-muted-foreground" />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium truncate">{leader.name}</p>
          <Badge variant="outline" className="text-xs">
            {leader.categoryName}
          </Badge>
        </div>
        <p className="text-xs text-muted-foreground">{leader.sku}</p>
        <Progress value={percentage} className="h-1 mt-2" />
      </div>

      <div className="flex-shrink-0 text-right space-y-1">
        <div className="flex items-center justify-end gap-1">
          {metricType === 'velocity' ? (
            <Activity className="h-3 w-3 text-muted-foreground" />
          ) : (
            <Zap className="h-3 w-3 text-muted-foreground" />
          )}
          <span className="text-sm font-semibold">
            {formatNumber(value, 2)}
            <span className="text-xs font-normal text-muted-foreground ml-1">
              {metricType === 'velocity' ? '/day' : 'x'}
            </span>
          </span>
        </div>
        <p className="text-xs text-muted-foreground">
          {leader.periodDemand} units ({formatNumber(leader.percentOfTotal)}%)
        </p>
        <div className={cn("text-xs flex items-center justify-end gap-1", getAccuracyColor(leader.forecastAccuracy))}>
          <Target className="h-3 w-3" />
          <span>{formatNumber(leader.forecastAccuracy)}%</span>
        </div>
      </div>
    </div>
  )
}

function DemandLeadersTable({
  leaders,
  isLoading,
  metricType,
}: {
  leaders?: DemandLeader[]
  isLoading: boolean
  metricType: 'velocity' | 'stock'
}) {
  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 p-3">
            <Skeleton className="h-8 w-8 rounded" />
            <Skeleton className="h-12 w-12 rounded-md" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-2 w-full" />
            </div>
            <Skeleton className="h-8 w-20" />
          </div>
        ))}
      </div>
    )
  }

  if (!leaders || leaders.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        No demand data available for this period.
      </div>
    )
  }

  const maxValue = metricType === 'velocity'
    ? Math.max(...leaders.map((l) => l.demandVelocity))
    : Math.max(...leaders.map((l) => l.stockVelocity))

  return (
    <div className="space-y-1">
      {leaders.map((leader) => (
        <DemandLeaderRow key={leader.itemId} leader={leader} maxValue={maxValue} metricType={metricType} />
      ))}
    </div>
  )
}

function CategoryRankingCard({
  rankings,
  isLoading,
}: {
  rankings?: CategoryRanking[]
  isLoading: boolean
}) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-40" />
        </CardHeader>
        <CardContent className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-2 w-full" />
            </div>
          ))}
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Category Rankings</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {rankings?.map((category) => (
          <div key={category.categoryId} className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground">#{category.rank}</span>
                <span className="font-medium">{category.categoryName}</span>
              </div>
              <div className="flex items-center gap-1">
                <Activity className="h-3 w-3 text-muted-foreground" />
                <span className="font-semibold">
                  {formatNumber(category.totalDemandVelocity, 2)}
                </span>
              </div>
            </div>
            <Progress value={category.percentOfTotal} className="h-2" />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{category.periodDemand} units ({category.totalItems} items)</span>
              <span>{formatNumber(category.percentOfTotal)}% of demand</span>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

export function TabDemandLeaders() {
  const [period, setPeriod] = useState<DemandLeadersPeriod>("30d")
  const { data, isLoading, isError } = useDemandLeaders(period)

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h3 className="text-lg font-semibold">Failed to load demand leaders</h3>
        <p className="text-muted-foreground">Please try again later.</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Demand Analysis</h2>
        <Select value={period} onValueChange={(v) => setPeriod(v as DemandLeadersPeriod)}>
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PERIOD_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <SummaryCards summary={data?.summary} isLoading={isLoading} />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card>
            <CardHeader className="pb-2">
              <Tabs defaultValue="velocity">
                <TabsList>
                  <TabsTrigger value="velocity">By Demand Velocity</TabsTrigger>
                  <TabsTrigger value="stock">By Stock Velocity</TabsTrigger>
                </TabsList>
              </Tabs>
            </CardHeader>
            <CardContent>
              <Tabs defaultValue="velocity">
                <TabsContent value="velocity" className="mt-0">
                  <DemandLeadersTable leaders={data?.byDemandVelocity} isLoading={isLoading} metricType="velocity" />
                </TabsContent>
                <TabsContent value="stock" className="mt-0">
                  <DemandLeadersTable leaders={data?.byStockVelocity} isLoading={isLoading} metricType="stock" />
                </TabsContent>
              </Tabs>
            </CardContent>
          </Card>
        </div>

        <div>
          <CategoryRankingCard rankings={data?.categoryRankings} isLoading={isLoading} />
        </div>
      </div>
    </div>
  )
}
