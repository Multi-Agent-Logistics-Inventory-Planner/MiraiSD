"use client"

import { useMemo, useRef, useState, useEffect } from "react"
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import type { DailySales } from "@/types/api"

interface CalendarHeatmapProps {
  data: DailySales[]
  year: number
  isLoading?: boolean
}

const MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
]

function getIntensityLevel(units: number, max: number): number {
  if (units === 0) return 0
  const ratio = units / max
  if (ratio <= 0.25) return 1
  if (ratio <= 0.5) return 2
  if (ratio <= 0.75) return 3
  return 4
}

function getIntensityClass(level: number): string {
  const classes: Record<number, string> = {
    0: "bg-[var(--sales-heatmap-0)]",
    1: "bg-[var(--sales-heatmap-1)]",
    2: "bg-[var(--sales-heatmap-2)]",
    3: "bg-[var(--sales-heatmap-3)]",
    4: "bg-[var(--sales-heatmap-4)]",
  }
  return classes[level] ?? classes[0]
}

interface WeekData {
  weekStart: Date
  days: { date: Date; units: number; level: number }[]
}

const GAP_SIZE = 2
const MIN_CELL_SIZE = 8
const MAX_CELL_SIZE = 14

function getYearPeriodLabel(year: number): string {
  const currentYear = new Date().getFullYear()
  if (year === currentYear) {
    return `${year} (Year to Date)`
  }
  return String(year)
}

export function CalendarHeatmap({
  data,
  year,
  isLoading,
}: CalendarHeatmapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [cellSize, setCellSize] = useState(10)

  const { weeks, monthLabels } = useMemo(() => {
    // Filter data for the selected year
    const yearData = data.filter((d) => new Date(d.date).getFullYear() === year)

    // Build sales map from filtered data
    const salesMap = new Map<string, number>()
    let max = 1
    for (const item of yearData) {
      salesMap.set(item.date, item.totalUnits)
      if (item.totalUnits > max) max = item.totalUnits
    }

    const today = new Date()
    const isCurrentYear = year === today.getFullYear()

    // Start from Jan 1 of selected year, aligned to Sunday
    const startDate = new Date(year, 0, 1)
    startDate.setDate(startDate.getDate() - startDate.getDay())

    // End at Dec 31 of year, or today if current year
    const endDate = isCurrentYear ? today : new Date(year, 11, 31)

    const weeksData: WeekData[] = []
    const months: { month: string; weekIndex: number }[] = []

    let currentDate = new Date(startDate)
    let weekIndex = 0
    let lastMonth = -1

    while (currentDate <= endDate) {
      const weekDays: { date: Date; units: number; level: number }[] = []
      const weekStart = new Date(currentDate)

      for (let day = 0; day < 7; day++) {
        const dateStr = currentDate.toISOString().split("T")[0]
        const units = salesMap.get(dateStr) ?? 0

        // Only track months within the selected year
        if (
          currentDate.getMonth() !== lastMonth &&
          currentDate <= endDate &&
          currentDate.getFullYear() === year
        ) {
          months.push({ month: MONTHS[currentDate.getMonth()], weekIndex })
          lastMonth = currentDate.getMonth()
        }

        weekDays.push({
          date: new Date(currentDate),
          units,
          level: getIntensityLevel(units, max),
        })

        currentDate.setDate(currentDate.getDate() + 1)
      }

      weeksData.push({ weekStart, days: weekDays })
      weekIndex++
    }

    return { weeks: weeksData, monthLabels: months }
  }, [data, year])

  useEffect(() => {
    const calculateCellSize = () => {
      if (!containerRef.current || weeks.length === 0) return

      const containerWidth = containerRef.current.offsetWidth
      const numWeeks = weeks.length
      const calculatedSize =
        (containerWidth - (numWeeks - 1) * GAP_SIZE) / numWeeks
      const clampedSize = Math.max(
        MIN_CELL_SIZE,
        Math.min(MAX_CELL_SIZE, Math.floor(calculatedSize))
      )
      setCellSize(clampedSize)
    }

    calculateCellSize()

    const resizeObserver = new ResizeObserver(calculateCellSize)
    if (containerRef.current) {
      resizeObserver.observe(containerRef.current)
    }

    return () => resizeObserver.disconnect()
  }, [weeks.length])

  if (isLoading) {
    return (
      <div className="flex h-[180px] items-center justify-center">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-sales-secondary border-t-transparent" />
      </div>
    )
  }

  const cellStyle = {
    width: cellSize,
    height: cellSize,
  }

  const gapStyle = { gap: GAP_SIZE }

  const gridWidth = weeks.length * cellSize + (weeks.length - 1) * GAP_SIZE

  return (
    <TooltipProvider delayDuration={50}>
      <div className="w-full" ref={containerRef}>
        {/* Legend */}
        <div className="mb-3 flex items-center justify-between">
          <span className="text-xs text-muted-foreground">
            {getYearPeriodLabel(year)}
          </span>
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span>Less</span>
            {[0, 1, 2, 3, 4].map((level) => (
              <div
                key={level}
                className={`h-3 w-3 rounded-[2px] ${getIntensityClass(level)}`}
              />
            ))}
            <span>More</span>
          </div>
        </div>

        {/* Heatmap Grid - Centered */}
        <div className="flex justify-center">
          <div
            className="flex flex-col"
            style={{ ...gapStyle, width: gridWidth }}
          >
            {/* Grid - 7 rows (days) x N columns (weeks) */}
            {[0, 1, 2, 3, 4, 5, 6].map((dayOfWeek) => (
              <div key={dayOfWeek} className="flex" style={gapStyle}>
                {weeks.map((week, weekIdx) => {
                  const day = week.days[dayOfWeek]
                  const today = new Date()
                  const isOutsideYear = day?.date.getFullYear() !== year
                  const isFutureDate = day?.date > today

                  if (!day || isOutsideYear || isFutureDate) {
                    return (
                      <div
                        key={weekIdx}
                        className="rounded-[2px] bg-transparent"
                        style={cellStyle}
                      />
                    )
                  }

                  return (
                    <Tooltip key={weekIdx}>
                      <TooltipTrigger asChild>
                        <div
                          className={`cursor-pointer rounded-[2px] transition-all hover:ring-1 hover:ring-sales-accent hover:ring-offset-1 ${getIntensityClass(day.level)}`}
                          style={cellStyle}
                        />
                      </TooltipTrigger>
                      <TooltipContent
                        className="border-none bg-zinc-900 px-3 py-2 text-sm text-white"
                        sideOffset={8}
                      >
                        <p className="font-semibold">
                          {day.units} {day.units === 1 ? "Sale" : "Sales"} on{" "}
                          {day.date.toLocaleDateString("en-US", {
                            day: "numeric",
                            month: "short",
                            year: "numeric",
                          })}
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  )
                })}
              </div>
            ))}

            {/* Month Labels Below */}
            <div className="mt-1 flex" style={gapStyle}>
              {weeks.map((_, weekIdx) => {
                const monthLabel = monthLabels.find(
                  (m) => m.weekIndex === weekIdx
                )
                return (
                  <div
                    key={weekIdx}
                    className="text-center"
                    style={{ width: cellSize }}
                  >
                    {monthLabel && (
                      <span className="text-[9px] text-muted-foreground">
                        {monthLabel.month}
                      </span>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}
