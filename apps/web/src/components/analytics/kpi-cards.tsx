"use client"

import { AlertTriangle, DollarSign, Target, CheckCircle } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

interface KPICardsProps {
  itemsAtRisk: number
  reorderValue: number
  forecastAccuracy: number
  fillRate: number
  isLoading?: boolean
}

function getAccuracyColor(accuracy: number): string {
  if (accuracy >= 85) return "text-green-600"
  if (accuracy >= 70) return "text-amber-600"
  return "text-red-600"
}

function getFillRateColor(rate: number): string {
  if (rate >= 95) return "text-green-600"
  if (rate >= 85) return "text-amber-600"
  return "text-red-600"
}

function getRiskColor(count: number): string {
  if (count === 0) return "text-green-600"
  if (count <= 5) return "text-amber-600"
  return "text-red-600"
}

export function KPICards({
  itemsAtRisk,
  reorderValue,
  forecastAccuracy,
  fillRate,
  isLoading,
}: KPICardsProps) {
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

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Items at Risk</CardTitle>
          <AlertTriangle className={`h-4 w-4 ${itemsAtRisk > 0 ? "text-amber-500" : "text-green-500"}`} />
        </CardHeader>
        <CardContent>
          <div className={`text-2xl font-bold ${getRiskColor(itemsAtRisk)}`}>
            {itemsAtRisk}
          </div>
          <p className="text-xs text-muted-foreground">
            Predicted stockout within 7 days
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Reorder Value</CardTitle>
          <DollarSign className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            ${reorderValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}
          </div>
          <p className="text-xs text-muted-foreground">
            Estimated cost for urgent orders
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Forecast Accuracy</CardTitle>
          <Target className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className={`text-2xl font-bold ${getAccuracyColor(forecastAccuracy)}`}>
            {forecastAccuracy.toFixed(1)}%
          </div>
          <p className="text-xs text-muted-foreground">
            Target: 85%+
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium">Fill Rate</CardTitle>
          <CheckCircle className={`h-4 w-4 ${fillRate >= 95 ? "text-green-500" : "text-amber-500"}`} />
        </CardHeader>
        <CardContent>
          <div className={`text-2xl font-bold ${getFillRateColor(fillRate)}`}>
            {fillRate.toFixed(1)}%
          </div>
          <p className="text-xs text-muted-foreground">
            Items in stock (Target: 95%+)
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
