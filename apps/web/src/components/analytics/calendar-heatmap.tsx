"use client"

import { useMemo } from "react"
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import type { DailySales } from "@/types/api"

interface CalendarHeatmapProps {
  data: DailySales[]
  isLoading?: boolean
}

const DAYS_OF_WEEK = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

function getIntensityLevel(units: number): number {
  if (units === 0) return 0
  if (units <= 3) return 1
  if (units <= 7) return 2
  if (units <= 15) return 3
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

interface DayData {
  date: Date
  dateString: string
  units: number
  level: number
}

interface WeekData {
  days: DayData[]
  weekIndex: number
}

export function CalendarHeatmap({ data, isLoading }: CalendarHeatmapProps) {
  const { weeks, monthLabels } = useMemo(() => {
    const salesMap = new Map<string, number>()
    for (const item of data) {
      salesMap.set(item.date, item.totalUnits)
    }

    const today = new Date()
    const startDate = new Date(today)
    startDate.setFullYear(startDate.getFullYear() - 1)
    startDate.setDate(startDate.getDate() - startDate.getDay())

    const weeksData: WeekData[] = []
    const monthPositions: { month: number; weekIndex: number }[] = []

    let currentDate = new Date(startDate)
    let weekIndex = 0
    let lastMonth = -1

    while (currentDate <= today) {
      const week: DayData[] = []

      for (let dayOfWeek = 0; dayOfWeek < 7; dayOfWeek++) {
        if (currentDate > today) {
          break
        }

        const month = currentDate.getMonth()
        if (month !== lastMonth) {
          monthPositions.push({ month, weekIndex })
          lastMonth = month
        }

        const dateString = currentDate.toISOString().split("T")[0]
        const units = salesMap.get(dateString) ?? 0

        week.push({
          date: new Date(currentDate),
          dateString,
          units,
          level: getIntensityLevel(units),
        })

        currentDate.setDate(currentDate.getDate() + 1)
      }

      if (week.length > 0) {
        weeksData.push({ days: week, weekIndex })
        weekIndex++
      }
    }

    return { weeks: weeksData, monthLabels: monthPositions }
  }, [data])

  if (isLoading) {
    return (
      <div className="flex h-[140px] items-center justify-center">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-[var(--sales-secondary)] border-t-transparent" />
      </div>
    )
  }

  return (
    <TooltipProvider delayDuration={100}>
      <div className="overflow-x-auto">
        <div className="min-w-[700px]">
          <div className="mb-1 flex pl-8">
            {monthLabels.map((item, idx) => {
              const nextItem = monthLabels[idx + 1]
              const width = nextItem
                ? (nextItem.weekIndex - item.weekIndex) * 14
                : (weeks.length - item.weekIndex) * 14

              return (
                <div
                  key={`${item.month}-${item.weekIndex}`}
                  className="text-xs text-muted-foreground"
                  style={{ width: `${width}px`, minWidth: `${width}px` }}
                >
                  {MONTHS[item.month]}
                </div>
              )
            })}
          </div>

          <div className="flex">
            <div className="flex flex-col justify-between pr-2 text-xs text-muted-foreground">
              {DAYS_OF_WEEK.filter((_, i) => i % 2 === 1).map((day) => (
                <span key={day} className="h-3 leading-3">
                  {day}
                </span>
              ))}
            </div>

            <div className="flex gap-[2px]">
              {weeks.map((week) => (
                <div key={week.weekIndex} className="flex flex-col gap-[2px]">
                  {week.days.map((day) => (
                    <Tooltip key={day.dateString}>
                      <TooltipTrigger asChild>
                        <div
                          className={`h-3 w-3 rounded-sm transition-colors ${getIntensityClass(day.level)}`}
                        />
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className="font-medium">
                          {day.units} {day.units === 1 ? "sale" : "sales"}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {day.date.toLocaleDateString("en-US", {
                            weekday: "short",
                            month: "short",
                            day: "numeric",
                            year: "numeric",
                          })}
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  ))}
                </div>
              ))}
            </div>
          </div>

          <div className="mt-2 flex items-center justify-end gap-1 text-xs text-muted-foreground">
            <span>Less</span>
            {[0, 1, 2, 3, 4].map((level) => (
              <div
                key={level}
                className={`h-3 w-3 rounded-sm ${getIntensityClass(level)}`}
              />
            ))}
            <span>More</span>
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}
