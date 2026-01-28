"use client"

import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Tooltip,
  Legend,
} from "recharts"
import type { ForecastPrediction } from "@/types/api"

interface RiskDistributionChartProps {
  forecasts: ForecastPrediction[]
  isLoading?: boolean
}

interface RiskSegment {
  name: string
  value: number
  color: string
  description: string
}

const RISK_COLORS = {
  critical: "#ef4444",
  warning: "#f59e0b",
  healthy: "#eab308",
  safe: "#22c55e",
  overstocked: "#3b82f6",
}

function categorizeByRisk(forecasts: ForecastPrediction[]): RiskSegment[] {
  const counts = {
    critical: 0,
    warning: 0,
    healthy: 0,
    safe: 0,
    overstocked: 0,
  }

  forecasts.forEach((forecast) => {
    const days = forecast.daysToStockout
    if (days <= 3) {
      counts.critical++
    } else if (days <= 7) {
      counts.warning++
    } else if (days <= 14) {
      counts.healthy++
    } else if (days <= 60) {
      counts.safe++
    } else {
      counts.overstocked++
    }
  })

  const segments: RiskSegment[] = [
    {
      name: "Critical",
      value: counts.critical,
      color: RISK_COLORS.critical,
      description: "< 3 days",
    },
    {
      name: "Warning",
      value: counts.warning,
      color: RISK_COLORS.warning,
      description: "3-7 days",
    },
    {
      name: "Healthy",
      value: counts.healthy,
      color: RISK_COLORS.healthy,
      description: "7-14 days",
    },
    {
      name: "Safe",
      value: counts.safe,
      color: RISK_COLORS.safe,
      description: "14-60 days",
    },
    {
      name: "Overstocked",
      value: counts.overstocked,
      color: RISK_COLORS.overstocked,
      description: "> 60 days",
    },
  ]

  return segments.filter((segment) => segment.value > 0)
}

interface CustomTooltipProps {
  active?: boolean
  payload?: Array<{
    name: string
    value: number
    payload: RiskSegment
  }>
}

function CustomTooltip({ active, payload }: CustomTooltipProps) {
  if (!active || !payload?.length) return null

  const data = payload[0].payload
  return (
    <div className="rounded-lg border bg-background p-2 shadow-md">
      <p className="font-medium">{data.name}</p>
      <p className="text-sm text-muted-foreground">{data.description}</p>
      <p className="text-sm font-semibold">{data.value} items</p>
    </div>
  )
}

interface CustomLegendProps {
  payload?: Array<{
    value: string
    color: string
    payload: RiskSegment
  }>
}

function CustomLegend({ payload }: CustomLegendProps) {
  if (!payload) return null

  return (
    <div className="flex flex-wrap justify-center gap-4 text-sm">
      {payload.map((entry, index) => (
        <div key={index} className="flex items-center gap-2">
          <div
            className="h-3 w-3 rounded-full"
            style={{ backgroundColor: entry.color }}
          />
          <span>
            {entry.value} ({entry.payload.value})
          </span>
        </div>
      ))}
    </div>
  )
}

export function RiskDistributionChart({
  forecasts,
  isLoading,
}: RiskDistributionChartProps) {
  const chartData = useMemo(() => categorizeByRisk(forecasts), [forecasts])
  const totalItems = forecasts.length

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Stock Distribution by Risk</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex h-[300px] items-center justify-center">
            <Skeleton className="h-[200px] w-[200px] rounded-full" />
          </div>
        </CardContent>
      </Card>
    )
  }

  if (forecasts.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Stock Distribution by Risk</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex h-[300px] items-center justify-center text-muted-foreground">
            No forecast data available
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Stock Distribution by Risk</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[300px]">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={chartData}
                cx="50%"
                cy="45%"
                innerRadius={60}
                outerRadius={100}
                paddingAngle={2}
                dataKey="value"
              >
                {chartData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
              <Legend
                content={<CustomLegend />}
                verticalAlign="bottom"
                height={36}
              />
              <text
                x="50%"
                y="45%"
                textAnchor="middle"
                dominantBaseline="middle"
                className="fill-foreground text-3xl font-bold"
              >
                {totalItems}
              </text>
              <text
                x="50%"
                y="52%"
                textAnchor="middle"
                dominantBaseline="middle"
                className="fill-muted-foreground text-sm"
              >
                SKUs
              </text>
            </PieChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
