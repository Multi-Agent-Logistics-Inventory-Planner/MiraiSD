"use client"

import { useMemo, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
  ReferenceLine,
  CartesianGrid,
} from "recharts"
import { TrendingUp, TrendingDown, BarChart3 } from "lucide-react"
import { CalendarHeatmap } from "./calendar-heatmap"
import { cn } from "@/lib/utils"
import type { SalesSummary, MonthlySales, DailySales } from "@/types/api"

interface SalesMetricsCardProps {
  data: SalesSummary | undefined
  isLoading?: boolean
}

type BarTimeFilter = "3M" | "6M" | "1Y" | "YTD"
type ViewMode = "bar" | "line"

const BAR_FILTER_OPTIONS = ["3M", "6M", "1Y", "YTD"] as const

interface TimeFilterTabsProps<T extends string> {
  value: T
  onChange: (value: T) => void
  options: readonly T[]
}

function TimeFilterTabs<T extends string>({
  value,
  onChange,
  options,
}: TimeFilterTabsProps<T>) {
  return (
    <div className="flex gap-1 rounded-md bg-muted p-1">
      {options.map((option) => (
        <button
          key={option}
          onClick={() => onChange(option)}
          className={cn(
            "px-2 py-0.5 text-xs font-medium rounded transition-colors",
            value === option
              ? "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          )}
        >
          {option}
        </button>
      ))}
    </div>
  )
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
    return `$${(value / 1000).toFixed(0)}k`
  }
  return `$${value.toFixed(0)}`
}

interface ChartDataItem {
  month: string
  label: string
  revenue: number
  units: number
  isCurrent: boolean
}

interface ViewToggleProps {
  value: ViewMode
  onChange: (value: ViewMode) => void
}

function ViewToggle({ value, onChange }: ViewToggleProps) {
  return (
    <div className="flex gap-1 rounded-md bg-muted p-1">
      <button
        onClick={() => onChange("bar")}
        className={cn(
          "p-1 rounded transition-colors",
          value === "bar"
            ? "bg-background shadow-sm"
            : "text-muted-foreground hover:text-foreground"
        )}
        aria-label="Bar chart view"
      >
        <BarChart3 className="h-4 w-4" />
      </button>
      <button
        onClick={() => onChange("line")}
        className={cn(
          "p-1 rounded transition-colors",
          value === "line"
            ? "bg-background shadow-sm"
            : "text-muted-foreground hover:text-foreground"
        )}
        aria-label="Trend line view"
      >
        <TrendingUp className="h-4 w-4" />
      </button>
    </div>
  )
}

function CustomTooltip({
  active,
  payload,
}: {
  active?: boolean
  payload?: Array<{ value: number; payload: ChartDataItem }>
}) {
  if (!active || !payload || !payload.length) return null

  const data = payload[0].payload
  const monthDate = new Date(data.month + "-01")
  const monthName = monthDate.toLocaleDateString("en-US", {
    month: "long",
    year: "numeric",
  })

  return (
    <div className="rounded-lg bg-zinc-900 px-3 py-2 text-sm text-white shadow-lg">
      <p className="font-semibold">
        {formatCurrency(data.revenue)} on {monthName}
      </p>
    </div>
  )
}

interface LineChartDataItem extends ChartDataItem {
  previousRevenue?: number
}

interface WeeklyChartDataItem {
  weekStart: string
  label: string
  revenue: number
  previousRevenue: number
}

function LineChartTooltip({
  active,
  payload,
}: {
  active?: boolean
  payload?: Array<{ dataKey: string; value: number; payload: WeeklyChartDataItem }>
}) {
  if (!active || !payload || !payload.length) return null

  const data = payload[0].payload
  if (!data?.weekStart) return null

  const weekDate = new Date(data.weekStart)
  const weekLabel = weekDate.toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  })

  const currentValue = payload.find((p) => p.dataKey === "revenue")
  const previousValue = payload.find((p) => p.dataKey === "previousRevenue")
  const currentRevenue = currentValue?.value ?? 0
  const previousRevenue = previousValue?.value ?? 0

  return (
    <div className="rounded-lg bg-zinc-900 px-3 py-2 text-sm text-white shadow-lg">
      <p className="font-semibold">Week of {weekLabel}</p>
      {currentValue && !isNaN(currentRevenue) && (
        <p className="text-purple-300">
          Current: {formatCurrency(currentRevenue)}
        </p>
      )}
      {previousValue && previousRevenue > 0 && !isNaN(previousRevenue) && (
        <p className="text-gray-400">
          Previous: {formatCurrency(previousRevenue)}
        </p>
      )}
    </div>
  )
}

function filterMonthlyData(
  monthlySales: MonthlySales[],
  filter: BarTimeFilter
): MonthlySales[] {
  const now = new Date()

  if (filter === "YTD") {
    return monthlySales.filter((item) => {
      const [year] = item.month.split("-")
      return parseInt(year, 10) === now.getFullYear()
    })
  }

  const monthsMap: Record<Exclude<BarTimeFilter, "YTD">, number> = {
    "3M": 3,
    "6M": 6,
    "1Y": 12,
  }

  return monthlySales.slice(-monthsMap[filter])
}

interface WeeklySales {
  weekStart: string
  weekLabel: string
  totalUnits: number
  totalRevenue: number
}

function aggregateByWeek(
  dailySales: DailySales[],
  filter: BarTimeFilter
): WeeklySales[] {
  const now = new Date()
  let startDate: Date

  if (filter === "YTD") {
    startDate = new Date(now.getFullYear(), 0, 1)
  } else {
    const monthsMap = { "3M": 3, "6M": 6, "1Y": 12 }
    startDate = new Date(now)
    startDate.setMonth(startDate.getMonth() - monthsMap[filter])
  }

  const filtered = dailySales.filter((d) => {
    const date = new Date(d.date)
    return date >= startDate && date <= now
  })

  const weekMap = new Map<string, { units: number; revenue: number }>()

  for (const day of filtered) {
    const date = new Date(day.date)
    const sunday = new Date(date)
    sunday.setDate(date.getDate() - date.getDay())
    const weekKey = sunday.toISOString().split("T")[0]

    const existing = weekMap.get(weekKey) ?? { units: 0, revenue: 0 }
    weekMap.set(weekKey, {
      units: existing.units + (day.totalUnits ?? 0),
      revenue: existing.revenue + (day.totalRevenue ?? 0),
    })
  }

  return Array.from(weekMap.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([weekStart, data]) => ({
      weekStart,
      weekLabel: new Date(weekStart).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
      }),
      totalUnits: data.units,
      totalRevenue: data.revenue,
    }))
}

function getPreviousPeriodWeekly(
  dailySales: DailySales[],
  filter: BarTimeFilter
): WeeklySales[] {
  const now = new Date()
  let startDate: Date
  let endDate: Date

  if (filter === "YTD") {
    startDate = new Date(now.getFullYear() - 1, 0, 1)
    endDate = new Date(now.getFullYear() - 1, now.getMonth(), now.getDate())
  } else {
    const monthsMap = { "3M": 3, "6M": 6, "1Y": 12 }
    const months = monthsMap[filter]
    endDate = new Date(now)
    endDate.setMonth(endDate.getMonth() - months)
    startDate = new Date(endDate)
    startDate.setMonth(startDate.getMonth() - months)
  }

  const filtered = dailySales.filter((d) => {
    const date = new Date(d.date)
    return date >= startDate && date <= endDate
  })

  const weekMap = new Map<string, { units: number; revenue: number }>()

  for (const day of filtered) {
    const date = new Date(day.date)
    const sunday = new Date(date)
    sunday.setDate(date.getDate() - date.getDay())
    const weekKey = sunday.toISOString().split("T")[0]

    const existing = weekMap.get(weekKey) ?? { units: 0, revenue: 0 }
    weekMap.set(weekKey, {
      units: existing.units + (day.totalUnits ?? 0),
      revenue: existing.revenue + (day.totalRevenue ?? 0),
    })
  }

  return Array.from(weekMap.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([weekStart, data]) => ({
      weekStart,
      weekLabel: new Date(weekStart).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
      }),
      totalUnits: data.units,
      totalRevenue: data.revenue,
    }))
}

export function SalesMetricsCard({ data, isLoading }: SalesMetricsCardProps) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const [barFilter, setBarFilter] = useState<BarTimeFilter>("1Y")
  const [viewMode, setViewMode] = useState<ViewMode>("bar")
  const [heatmapYear, setHeatmapYear] = useState<number | null>(null)

  // Get unique years from dailySales data for heatmap filter
  const availableYears = useMemo(() => {
    if (!data?.dailySales) return []

    const years = new Set<number>()
    for (const day of data.dailySales) {
      const year = new Date(day.date).getFullYear()
      years.add(year)
    }

    return Array.from(years).sort((a, b) => b - a) // Descending (newest first)
  }, [data])

  // Default to most recent year with data
  const selectedYear = heatmapYear ?? availableYears[0] ?? new Date().getFullYear()

  const {
    chartData,
    lineChartData,
    filteredRevenue,
    yearOverYearChange,
    periodLabel,
    hasPreviousPeriodData,
  } = useMemo(() => {
    if (!data?.monthlySales) {
      return {
        chartData: [],
        lineChartData: [],
        filteredRevenue: 0,
        yearOverYearChange: 0,
        periodLabel: "",
        hasPreviousPeriodData: false,
      }
    }

    const now = new Date()
    const currentKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`

    const filtered = filterMonthlyData(data.monthlySales, barFilter)

    const formattedData: ChartDataItem[] = filtered.map((item) => {
      const monthDate = new Date(item.month + "-01")
      const monthLabel = monthDate.toLocaleDateString("en-US", {
        month: "short",
      })
      return {
        month: item.month,
        label: monthLabel,
        revenue: item.totalRevenue,
        units: item.totalUnits,
        isCurrent: item.month === currentKey,
      }
    })

    const revenue = filtered.reduce((sum, m) => sum + m.totalRevenue, 0)

    // Build previous period data for line chart comparison
    let previousPeriodData: MonthlySales[] = []
    let change = 0
    let hasPrevData = false

    if (barFilter === "YTD") {
      const lastYearYTD = data.monthlySales.filter((item) => {
        const [year, month] = item.month.split("-")
        return (
          parseInt(year, 10) === now.getFullYear() - 1 &&
          parseInt(month, 10) <= now.getMonth() + 1
        )
      })
      previousPeriodData = lastYearYTD
      const lastYearRevenue = lastYearYTD.reduce(
        (sum, m) => sum + m.totalRevenue,
        0
      )
      hasPrevData = lastYearYTD.length > 0 && lastYearRevenue > 0
      if (lastYearRevenue > 0) {
        change = ((revenue - lastYearRevenue) / lastYearRevenue) * 100
      }
    } else {
      const monthsCount = { "3M": 3, "6M": 6, "1Y": 12 }[barFilter] ?? 12
      const startIdx = data.monthlySales.length - monthsCount * 2
      const endIdx = data.monthlySales.length - monthsCount
      if (startIdx >= 0) {
        previousPeriodData = data.monthlySales.slice(startIdx, endIdx)
        const previousRevenue = previousPeriodData.reduce(
          (sum, m) => sum + m.totalRevenue,
          0
        )
        hasPrevData =
          previousPeriodData.length > 0 &&
          previousPeriodData.some((m) => m.totalRevenue > 0)
        if (previousRevenue > 0) {
          change = ((revenue - previousRevenue) / previousRevenue) * 100
        }
      }
    }

    // Build line chart data with previous period values
    const lineData: LineChartDataItem[] = formattedData.map((item, index) => {
      const prevItem = previousPeriodData[index]
      return {
        ...item,
        previousRevenue: prevItem?.totalRevenue ?? 0,
      }
    })

    const periodLabels: Record<BarTimeFilter, string> = {
      "3M": "previous 3 months",
      "6M": "previous 6 months",
      "1Y": "previous year",
      YTD: "same period last year",
    }

    return {
      chartData: formattedData,
      lineChartData: lineData,
      filteredRevenue: revenue,
      yearOverYearChange: change,
      periodLabel: periodLabels[barFilter],
      hasPreviousPeriodData: hasPrevData,
    }
  }, [data, barFilter])

  const { heatmapTotalUnits, heatmapPeriodLabel } = useMemo(() => {
    if (!data?.dailySales) {
      return { heatmapTotalUnits: 0, heatmapPeriodLabel: "" }
    }

    const yearData = data.dailySales.filter(
      (day) => new Date(day.date).getFullYear() === selectedYear
    )

    const totalUnits = yearData.reduce((sum, d) => sum + d.totalUnits, 0)

    const isCurrentYear = selectedYear === new Date().getFullYear()
    const periodLabel = isCurrentYear
      ? `Total in ${selectedYear} (YTD)`
      : `Total in ${selectedYear}`

    return {
      heatmapTotalUnits: totalUnits,
      heatmapPeriodLabel: periodLabel,
    }
  }, [data, selectedYear])

  const { weeklyChartData, hasPreviousWeeklyData } = useMemo(() => {
    if (!data?.dailySales) {
      return { weeklyChartData: [], hasPreviousWeeklyData: false }
    }

    const currentWeeks = aggregateByWeek(data.dailySales, barFilter)
    const previousWeeks = getPreviousPeriodWeekly(data.dailySales, barFilter)

    const chartData: WeeklyChartDataItem[] = currentWeeks.map((week, idx) => ({
      weekStart: week.weekStart,
      label: week.weekLabel,
      revenue: week.totalRevenue,
      previousRevenue: previousWeeks[idx]?.totalRevenue ?? 0,
    }))

    const hasPrevData = previousWeeks.some((w) => w.totalRevenue > 0)

    return { weeklyChartData: chartData, hasPreviousWeeklyData: hasPrevData }
  }, [data, barFilter])

  if (isLoading) {
    return (
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="border">
          <CardContent className="p-6">
            <Skeleton className="mb-6 h-5 w-24" />
            <Skeleton className="mb-2 h-10 w-40" />
            <Skeleton className="mb-6 h-4 w-32" />
            <Skeleton className="h-[200px] w-full" />
          </CardContent>
        </Card>
        <Card className="border">
          <CardContent className="p-6">
            <Skeleton className="mb-6 h-5 w-24" />
            <Skeleton className="mb-2 h-10 w-32" />
            <Skeleton className="mb-6 h-4 w-40" />
            <Skeleton className="h-[180px] w-full" />
          </CardContent>
        </Card>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="border">
          <CardContent className="flex h-[320px] items-center justify-center p-6">
            <p className="text-muted-foreground">No sales data available</p>
          </CardContent>
        </Card>
        <Card className="border">
          <CardContent className="flex h-[320px] items-center justify-center p-6">
            <p className="text-muted-foreground">No trend data available</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  const isPositiveChange = yearOverYearChange >= 0

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {/* Total Sales - Bar/Line Chart */}
      <Card className="border">
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-base font-medium">Total Sales</CardTitle>
          <div className="flex items-center gap-2">
            <ViewToggle value={viewMode} onChange={setViewMode} />
            <TimeFilterTabs
              value={barFilter}
              onChange={setBarFilter}
              options={BAR_FILTER_OPTIONS}
            />
          </div>
        </CardHeader>
        <CardContent>
          <div className="mb-6">
            <p className="text-4xl font-bold tracking-tight">
              {formatCurrency(filteredRevenue)}
            </p>
            {hasPreviousPeriodData && (
              <div className="mt-1 flex items-center gap-1">
                {isPositiveChange ? (
                  <TrendingUp className="h-4 w-4 text-emerald-500" />
                ) : (
                  <TrendingDown className="h-4 w-4 text-red-500" />
                )}
                <span
                  className={cn(
                    "text-sm font-medium",
                    isPositiveChange ? "text-emerald-500" : "text-red-500"
                  )}
                >
                  {isPositiveChange ? "+" : ""}
                  {yearOverYearChange.toFixed(0)}%
                </span>
                <span className="text-sm text-muted-foreground">
                  {periodLabel}
                </span>
              </div>
            )}
          </div>

          <div className="h-[220px]">
            <ResponsiveContainer width="100%" height="100%">
              {viewMode === "bar" ? (
                <BarChart
                  data={chartData}
                  margin={{ top: 10, right: 10, bottom: 0, left: -10 }}
                  onMouseLeave={() => setActiveIndex(null)}
                >
                  <XAxis
                    dataKey="label"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 12, fill: "#9ca3af" }}
                  />
                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 12, fill: "#9ca3af" }}
                    tickFormatter={formatCompactCurrency}
                    width={45}
                  />
                  {activeIndex !== null && chartData[activeIndex] && (
                    <ReferenceLine
                      y={chartData[activeIndex].revenue}
                      stroke="var(--sales-accent)"
                      strokeDasharray="4 4"
                    />
                  )}
                  <Tooltip
                    content={<CustomTooltip />}
                    cursor={{ fill: "transparent" }}
                  />
                  <Bar
                    dataKey="revenue"
                    radius={[4, 4, 0, 0]}
                    maxBarSize={40}
                    onMouseEnter={(_, index) => setActiveIndex(index)}
                  >
                    {chartData.map((entry, index) => (
                      <Cell
                        key={entry.month}
                        fill={
                          index === activeIndex
                            ? "var(--sales-accent)"
                            : "var(--sales-secondary)"
                        }
                        style={{ cursor: "pointer" }}
                      />
                    ))}
                  </Bar>
                </BarChart>
              ) : (
                <LineChart
                  data={weeklyChartData}
                  margin={{ top: 10, right: 10, bottom: 0, left: -10 }}
                >
                  <CartesianGrid
                    strokeDasharray="3 3"
                    vertical={false}
                    stroke="#e5e7eb"
                  />
                  <XAxis
                    dataKey="label"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 10, fill: "#9ca3af" }}
                    interval="preserveStartEnd"
                  />
                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 12, fill: "#9ca3af" }}
                    tickFormatter={formatCompactCurrency}
                    width={45}
                  />
                  {hasPreviousWeeklyData && (
                    <Line
                      dataKey="previousRevenue"
                      stroke="#d1d5db"
                      strokeWidth={2}
                      dot={false}
                      type="linear"
                    />
                  )}
                  <Line
                    dataKey="revenue"
                    stroke="var(--sales-secondary)"
                    strokeWidth={2}
                    dot={{ fill: "var(--sales-secondary)", r: 3 }}
                    type="linear"
                  />
                  <Tooltip content={<LineChartTooltip />} />
                </LineChart>
              )}
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Sales Trend - Heatmap */}
      <Card className="border">
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-base font-medium">Sales Trend</CardTitle>
          {availableYears.length > 0 && (
            <TimeFilterTabs
              value={String(selectedYear)}
              onChange={(val) => setHeatmapYear(parseInt(val, 10))}
              options={availableYears.map(String)}
            />
          )}
        </CardHeader>
        <CardContent>
          <div className="mb-6">
            <p className="text-4xl font-bold tracking-tight">
              {heatmapTotalUnits.toLocaleString()}{" "}
              <span className="text-xl font-normal text-muted-foreground">
                Sales
              </span>
            </p>
            <p className="mt-1 text-sm text-muted-foreground">
              {heatmapPeriodLabel}
            </p>
          </div>

          <CalendarHeatmap data={data.dailySales} year={selectedYear} />
        </CardContent>
      </Card>
    </div>
  )
}
