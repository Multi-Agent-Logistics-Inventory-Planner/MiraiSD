"use client"

import { useMemo } from "react"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from "recharts"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { CategoryInventory } from "@/types/api"

interface CategoryChartProps {
  data: CategoryInventory[] | undefined
  isLoading?: boolean
}

const BAR_COLOR = "var(--chart-1, #6366f1)"

interface ChartEntry {
  category: string
  totalStock: number
  totalItems: number
  sharePercent: number
}

interface CustomTooltipProps {
  active?: boolean
  payload?: Array<{
    value: number
    payload: ChartEntry
  }>
}

function CustomTooltip({ active, payload }: CustomTooltipProps) {
  if (!active || !payload?.length) return null

  const data = payload[0].payload
  return (
    <div className="rounded-lg border bg-background px-3 py-2 shadow-lg">
      <p className="text-sm font-semibold text-foreground">{data.category}</p>
      <div className="mt-1 space-y-0.5">
        <p className="text-xs text-muted-foreground">
          {data.totalItems} item{data.totalItems !== 1 ? "s" : ""} registered
        </p>
        <p className="text-sm font-medium tabular-nums text-foreground">
          {data.totalStock.toLocaleString()} units in stock
        </p>
        <p className="text-xs tabular-nums text-muted-foreground">
          {data.sharePercent.toFixed(1)}% of total inventory
        </p>
      </div>
    </div>
  )
}

export function CategoryChart({ data, isLoading }: CategoryChartProps) {
  const chartData = useMemo((): ChartEntry[] => {
    if (!data) return []
    const totalStock = data.reduce((sum, c) => sum + c.totalStock, 0)
    return [...data]
      .filter((c) => c.totalStock > 0)
      .sort((a, b) => b.totalStock - a.totalStock)
      .map((c) => ({
        category: c.category,
        totalStock: c.totalStock,
        totalItems: c.totalItems,
        sharePercent: totalStock > 0 ? (c.totalStock / totalStock) * 100 : 0,
      }))
  }, [data])

  const emptyCount = useMemo(() => {
    if (!data) return 0
    return data.filter((c) => c.totalStock === 0).length
  }, [data])

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Inventory by Category</CardTitle>
          <CardDescription>Stock distribution across active categories</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-8 w-full" />
            ))}
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!data || data.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Inventory by Category</CardTitle>
          <CardDescription>Stock distribution across active categories</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex h-[200px] items-center justify-center text-muted-foreground">
            No category data available
          </div>
        </CardContent>
      </Card>
    )
  }

  if (chartData.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Inventory by Category</CardTitle>
          <CardDescription>Stock distribution across active categories</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex h-[200px] flex-col items-center justify-center gap-2 text-muted-foreground">
            <p className="text-sm">All {data.length} categories have 0 stock</p>
          </div>
        </CardContent>
      </Card>
    )
  }

  const chartHeight = Math.max(200, chartData.length * 52)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Inventory by Category</CardTitle>
        <CardDescription>
          {chartData.length} active categor{chartData.length === 1 ? "y" : "ies"}
          {emptyCount > 0 && (
            <span className="text-muted-foreground/70">
              {" "}({emptyCount} empty hidden)
            </span>
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div style={{ height: chartHeight }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              layout="vertical"
              margin={{ top: 0, right: 48, bottom: 0, left: 0 }}
              barCategoryGap="20%"
            >
              <defs>
                <linearGradient id="barGradient" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor={BAR_COLOR} stopOpacity={0.8} />
                  <stop offset="100%" stopColor={BAR_COLOR} stopOpacity={1} />
                </linearGradient>
              </defs>
              <CartesianGrid
                horizontal={false}
                strokeDasharray="3 3"
                stroke="var(--border)"
                opacity={0.5}
              />
              <XAxis
                type="number"
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                tickMargin={8}
              />
              <YAxis
                type="category"
                dataKey="category"
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 13, fill: "var(--foreground)", fontWeight: 500 }}
                width={100}
              />
              <Tooltip
                content={<CustomTooltip />}
                cursor={{ fill: "var(--accent)", opacity: 0.3 }}
              />
              <Bar
                dataKey="totalStock"
                fill="url(#barGradient)"
                radius={[0, 6, 6, 0]}
                maxBarSize={36}
                label={{
                  position: "right",
                  fontSize: 12,
                  fontWeight: 600,
                  fill: "var(--muted-foreground)",
                  formatter: (value: number) => value.toLocaleString(),
                }}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
