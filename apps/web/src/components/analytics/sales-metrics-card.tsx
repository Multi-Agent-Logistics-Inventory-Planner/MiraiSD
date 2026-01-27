"use client"

import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts"
import { TrendingUp, TrendingDown } from "lucide-react"
import { CalendarHeatmap } from "./calendar-heatmap"
import type { SalesSummary } from "@/types/api"

interface SalesMetricsCardProps {
  data: SalesSummary | undefined
  isLoading?: boolean
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
  }).format(value)
}

function formatCompactCurrency(value: number): string {
  if (value >= 1000) {
    return `$${(value / 1000).toFixed(1)}k`
  }
  return formatCurrency(value)
}

interface ChartDataItem {
  month: string
  shortMonth: string
  revenue: number
  units: number
  isCurrent: boolean
}

export function SalesMetricsCard({ data, isLoading }: SalesMetricsCardProps) {
  const { chartData, yearOverYearChange } = useMemo(() => {
    if (!data?.monthlySales) {
      return { chartData: [], yearOverYearChange: 0 }
    }

    const now = new Date()
    const currentKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`

    const formattedData: ChartDataItem[] = data.monthlySales.map((item) => {
      const [year, month] = item.month.split("-")
      const date = new Date(parseInt(year), parseInt(month) - 1)
      const shortMonth = date.toLocaleDateString("en-US", { month: "short" })

      return {
        month: item.month,
        shortMonth,
        revenue: item.totalRevenue,
        units: item.totalUnits,
        isCurrent: item.month === currentKey,
      }
    })

    const currentMonthData = formattedData.find((d) => d.isCurrent)
    const lastYearMonth = `${now.getFullYear() - 1}-${String(now.getMonth() + 1).padStart(2, "0")}`
    const lastYearData = formattedData.find((d) => d.month === lastYearMonth)

    let change = 0
    if (lastYearData && lastYearData.revenue > 0 && currentMonthData) {
      change = ((currentMonthData.revenue - lastYearData.revenue) / lastYearData.revenue) * 100
    }

    return {
      chartData: formattedData,
      yearOverYearChange: change,
    }
  }, [data])

  if (isLoading) {
    return (
      <Card>
        <CardContent className="p-6">
          <div className="grid gap-6 lg:grid-cols-2">
            <div>
              <Skeleton className="mb-4 h-6 w-32" />
              <Skeleton className="mb-2 h-10 w-48" />
              <Skeleton className="mb-4 h-4 w-24" />
              <Skeleton className="h-[200px] w-full" />
            </div>
            <div>
              <Skeleton className="mb-4 h-6 w-32" />
              <Skeleton className="mb-4 h-8 w-36" />
              <Skeleton className="h-[140px] w-full" />
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!data) {
    return (
      <Card>
        <CardContent className="flex h-[300px] items-center justify-center p-6">
          <p className="text-muted-foreground">No sales data available</p>
        </CardContent>
      </Card>
    )
  }

  const isPositiveChange = yearOverYearChange >= 0

  return (
    <Card>
      <CardContent className="p-6">
        <div className="grid gap-6 lg:grid-cols-2">
          <div>
            <CardHeader className="p-0 pb-4">
              <CardTitle className="text-lg font-semibold">Total Sales</CardTitle>
            </CardHeader>

            <div className="mb-4">
              <p
                className="text-3xl font-bold"
                style={{ color: "var(--sales-primary)" }}
              >
                {formatCurrency(data.totalRevenue)}
              </p>
              <div className="mt-1 flex items-center gap-1">
                {isPositiveChange ? (
                  <TrendingUp className="h-4 w-4 text-green-600" />
                ) : (
                  <TrendingDown className="h-4 w-4 text-red-600" />
                )}
                <span
                  className={`text-sm font-medium ${
                    isPositiveChange ? "text-green-600" : "text-red-600"
                  }`}
                >
                  {isPositiveChange ? "+" : ""}
                  {yearOverYearChange.toFixed(1)}% vs last year
                </span>
              </div>
            </div>

            <div className="h-[200px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} margin={{ top: 5, right: 5, bottom: 5, left: 0 }}>
                  <XAxis
                    dataKey="shortMonth"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                    interval="preserveStartEnd"
                  />
                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                    tickFormatter={formatCompactCurrency}
                    width={50}
                  />
                  <Tooltip
                    formatter={(value: number) => [formatCurrency(value), "Revenue"]}
                    labelFormatter={(label) => `Month: ${label}`}
                    contentStyle={{
                      backgroundColor: "var(--card)",
                      border: "1px solid var(--border)",
                      borderRadius: "var(--radius)",
                    }}
                  />
                  <Bar dataKey="revenue" radius={[4, 4, 0, 0]}>
                    {chartData.map((entry) => (
                      <Cell
                        key={entry.month}
                        fill={
                          entry.isCurrent
                            ? "var(--sales-accent)"
                            : "var(--sales-secondary)"
                        }
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div>
            <CardHeader className="p-0 pb-4">
              <CardTitle className="text-lg font-semibold">Sales Trend</CardTitle>
            </CardHeader>

            <div className="mb-4">
              <p
                className="text-2xl font-bold"
                style={{ color: "var(--sales-primary)" }}
              >
                {data.totalUnits.toLocaleString()} Sales
              </p>
              <p className="text-sm text-muted-foreground">
                Last 12 months
              </p>
            </div>

            <CalendarHeatmap data={data.dailySales} />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
