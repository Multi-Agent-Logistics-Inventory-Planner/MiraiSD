"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import type { PerformanceMetrics as PerformanceMetricsType } from "@/types/api"

interface PerformanceMetricsProps {
  metrics: PerformanceMetricsType | undefined
  isLoading?: boolean
}

interface MetricConfig {
  name: string
  key: keyof PerformanceMetricsType
  target: number
  unit: string
  description: string
  isInverse?: boolean
}

const METRICS_CONFIG: MetricConfig[] = [
  {
    name: "Forecast Accuracy",
    key: "forecastAccuracy",
    target: 85,
    unit: "%",
    description: "How accurate predictions are vs actual demand",
  },
  {
    name: "Fill Rate",
    key: "fillRate",
    target: 95,
    unit: "%",
    description: "Percentage of items currently in stock",
  },
  {
    name: "Stockout Rate",
    key: "stockoutRate",
    target: 5,
    unit: "%",
    description: "Percentage of items out of stock",
    isInverse: true,
  },
  {
    name: "Turnover Rate",
    key: "turnoverRate",
    target: 5,
    unit: "x",
    description: "How many times inventory is sold and replaced",
  },
]

function getProgressColor(value: number, target: number, isInverse?: boolean): string {
  const ratio = value / target

  if (isInverse) {
    if (ratio <= 1) return "[&>[data-slot=progress-indicator]]:bg-green-500"
    if (ratio <= 1.5) return "[&>[data-slot=progress-indicator]]:bg-amber-500"
    return "[&>[data-slot=progress-indicator]]:bg-red-500"
  }

  if (ratio >= 1) return "[&>[data-slot=progress-indicator]]:bg-green-500"
  if (ratio >= 0.8) return "[&>[data-slot=progress-indicator]]:bg-amber-500"
  return "[&>[data-slot=progress-indicator]]:bg-red-500"
}

function getStatusText(value: number, target: number, isInverse?: boolean): string {
  const ratio = value / target

  if (isInverse) {
    if (ratio <= 1) return "On Target"
    if (ratio <= 1.5) return "Needs Attention"
    return "Critical"
  }

  if (ratio >= 1) return "On Target"
  if (ratio >= 0.8) return "Needs Attention"
  return "Below Target"
}

function getStatusColor(value: number, target: number, isInverse?: boolean): string {
  const ratio = value / target

  if (isInverse) {
    if (ratio <= 1) return "text-green-600"
    if (ratio <= 1.5) return "text-amber-600"
    return "text-red-600"
  }

  if (ratio >= 1) return "text-green-600"
  if (ratio >= 0.8) return "text-amber-600"
  return "text-red-600"
}

export function PerformanceMetrics({
  metrics,
  isLoading,
}: PerformanceMetricsProps) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Performance Metrics</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="space-y-2">
              <div className="flex items-center justify-between">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-4 w-24" />
              </div>
              <Skeleton className="h-2 w-full" />
              <Skeleton className="h-3 w-48" />
            </div>
          ))}
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Performance Metrics</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {METRICS_CONFIG.map((config) => {
          const value = metrics?.[config.key] ?? 0
          const progressValue = config.isInverse
            ? Math.max(0, Math.min(100, (config.target / Math.max(value, 0.01)) * 100))
            : Math.min(100, (value / config.target) * 100)

          return (
            <div key={config.key} className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">{config.name}</span>
                  <span
                    className={`text-xs ${getStatusColor(value, config.target, config.isInverse)}`}
                  >
                    {getStatusText(value, config.target, config.isInverse)}
                  </span>
                </div>
                <span className="text-sm text-muted-foreground">
                  <span className="font-semibold text-foreground">
                    {value.toFixed(1)}{config.unit}
                  </span>
                  {" / "}
                  {config.target}{config.unit} target
                </span>
              </div>
              <div className="relative">
                <Progress
                  value={progressValue}
                  className={getProgressColor(value, config.target, config.isInverse)}
                />
                <div
                  className="absolute top-0 h-full w-px bg-foreground/30"
                  style={{ left: "100%" }}
                  title="Target"
                />
              </div>
              <p className="text-xs text-muted-foreground">{config.description}</p>
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}
