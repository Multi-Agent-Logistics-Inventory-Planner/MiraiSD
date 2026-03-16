"use client"

import { Settings, Info, AlertCircle } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { usePerformanceMetrics } from "@/hooks/queries/use-analytics"
import { cn } from "@/lib/utils"

function MetricGauge({
  label,
  value,
  description,
  targetMin,
  targetMax,
  unit = "%",
  isLoading,
}: {
  label: string
  value: number
  description: string
  targetMin: number
  targetMax: number
  unit?: string
  isLoading: boolean
}) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <Skeleton className="h-5 w-32" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-8 w-20" />
          <Skeleton className="h-2 w-full mt-2" />
          <Skeleton className="h-3 w-40 mt-2" />
        </CardContent>
      </Card>
    )
  }

  const isInRange = value >= targetMin && value <= targetMax
  const isBelowTarget = value < targetMin
  const isAboveTarget = value > targetMax

  const getStatusColor = () => {
    if (isInRange) return "text-green-600"
    if (isBelowTarget) return "text-amber-600"
    return "text-red-600"
  }

  const getStatusBadge = () => {
    if (isInRange) return <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">On Target</Badge>
    if (isBelowTarget) return <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200">Below Target</Badge>
    return <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">Above Target</Badge>
  }

  // Calculate position on the gauge (0-100)
  const gaugePosition = Math.min(100, Math.max(0, value))

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between pb-2">
        <div>
          <CardTitle className="text-sm font-medium">{label}</CardTitle>
          <CardDescription className="text-xs">{description}</CardDescription>
        </div>
        {getStatusBadge()}
      </CardHeader>
      <CardContent>
        <div className={cn("text-3xl font-bold", getStatusColor())}>
          {value.toFixed(1)}{unit}
        </div>
        <div className="relative mt-3 h-2 bg-muted rounded-full overflow-hidden">
          {/* Target range indicator */}
          <div
            className="absolute h-full bg-green-200 opacity-50"
            style={{
              left: `${targetMin}%`,
              width: `${targetMax - targetMin}%`,
            }}
          />
          {/* Current value indicator */}
          <div
            className={cn(
              "absolute w-3 h-3 rounded-full -top-0.5 -translate-x-1/2",
              isInRange ? "bg-green-500" : isBelowTarget ? "bg-amber-500" : "bg-red-500"
            )}
            style={{ left: `${gaugePosition}%` }}
          />
        </div>
        <div className="flex justify-between mt-1 text-xs text-muted-foreground">
          <span>0{unit}</span>
          <span>Target: {targetMin}-{targetMax}{unit}</span>
          <span>100{unit}</span>
        </div>
      </CardContent>
    </Card>
  )
}

function ForecastSettings() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium flex items-center gap-2">
          <Settings className="h-4 w-4" />
          Forecast Configuration
        </CardTitle>
        <CardDescription>
          Settings are managed by the forecasting service
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <div className="p-4 rounded-lg bg-muted/50">
            <h4 className="text-sm font-medium">Prediction Horizon</h4>
            <p className="text-2xl font-bold mt-1">30 days</p>
            <p className="text-xs text-muted-foreground mt-1">
              How far into the future predictions are made
            </p>
          </div>
          <div className="p-4 rounded-lg bg-muted/50">
            <h4 className="text-sm font-medium">Reorder Lead Time</h4>
            <p className="text-2xl font-bold mt-1">7 days</p>
            <p className="text-xs text-muted-foreground mt-1">
              Default time for orders to arrive
            </p>
          </div>
          <div className="p-4 rounded-lg bg-muted/50">
            <h4 className="text-sm font-medium">Safety Stock Factor</h4>
            <p className="text-2xl font-bold mt-1">1.5x</p>
            <p className="text-xs text-muted-foreground mt-1">
              Multiplier for safety stock calculation
            </p>
          </div>
          <div className="p-4 rounded-lg bg-muted/50">
            <h4 className="text-sm font-medium">Update Frequency</h4>
            <p className="text-2xl font-bold mt-1">Daily</p>
            <p className="text-xs text-muted-foreground mt-1">
              Forecasts refresh every night at 2 AM
            </p>
          </div>
        </div>

        <div className="flex items-start gap-3 p-4 rounded-lg bg-blue-50 dark:bg-blue-950 border border-blue-200 dark:border-blue-800">
          <Info className="h-5 w-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-blue-800 dark:text-blue-200">
              Advanced Settings
            </p>
            <p className="text-xs text-blue-700 dark:text-blue-300 mt-1">
              To modify forecast parameters, contact your system administrator or update the
              forecasting service configuration directly.
            </p>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function AlertThresholds() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium flex items-center gap-2">
          <AlertCircle className="h-4 w-4" />
          Alert Thresholds
        </CardTitle>
        <CardDescription>
          When notifications are triggered
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center justify-between p-3 rounded-lg bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800">
          <div>
            <p className="text-sm font-medium text-red-800 dark:text-red-200">Critical</p>
            <p className="text-xs text-red-700 dark:text-red-300">Stock out imminent</p>
          </div>
          <Badge className="bg-red-100 text-red-800 border-red-200">
            &lt; Lead Time
          </Badge>
        </div>

        <div className="flex items-center justify-between p-3 rounded-lg bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800">
          <div>
            <p className="text-sm font-medium text-amber-800 dark:text-amber-200">Urgent</p>
            <p className="text-xs text-amber-700 dark:text-amber-300">Order soon recommended</p>
          </div>
          <Badge className="bg-amber-100 text-amber-800 border-amber-200">
            &lt; 2x Lead Time
          </Badge>
        </div>

        <div className="flex items-center justify-between p-3 rounded-lg bg-yellow-50 dark:bg-yellow-950 border border-yellow-200 dark:border-yellow-800">
          <div>
            <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200">Attention</p>
            <p className="text-xs text-yellow-700 dark:text-yellow-300">Monitor closely</p>
          </div>
          <Badge className="bg-yellow-100 text-yellow-800 border-yellow-200">
            Below Reorder Point
          </Badge>
        </div>
      </CardContent>
    </Card>
  )
}

export function TabSettings() {
  const { data, isLoading, isError } = usePerformanceMetrics()

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h3 className="text-lg font-semibold">Failed to load settings</h3>
        <p className="text-muted-foreground">Please try again later.</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold mb-4">Performance Metrics</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <MetricGauge
            label="Forecast Accuracy"
            value={data?.forecastAccuracy ?? 0}
            description="Average confidence of demand predictions"
            targetMin={70}
            targetMax={95}
            isLoading={isLoading}
          />
          <MetricGauge
            label="Fill Rate"
            value={data?.fillRate ?? 0}
            description="Percentage of items in stock"
            targetMin={90}
            targetMax={100}
            isLoading={isLoading}
          />
          <MetricGauge
            label="Stockout Rate"
            value={data?.stockoutRate ?? 0}
            description="Percentage of items out of stock"
            targetMin={0}
            targetMax={10}
            isLoading={isLoading}
          />
          <MetricGauge
            label="Turnover Rate"
            value={data?.turnoverRate ?? 0}
            description="Annual inventory turnover (COGS / Avg Inventory)"
            targetMin={4}
            targetMax={12}
            unit="x"
            isLoading={isLoading}
          />
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <ForecastSettings />
        <AlertThresholds />
      </div>
    </div>
  )
}
