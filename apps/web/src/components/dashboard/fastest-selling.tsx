"use client"

import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import type { ForecastPrediction } from "@/types/api"

interface FastestSellingProps {
  forecasts: ForecastPrediction[]
  isLoading: boolean
}

function buildTopItems(forecasts: ForecastPrediction[]): ForecastPrediction[] {
  return forecasts
    .filter((f) => f.avgDailyDelta < 0)
    .slice()
    .sort((a, b) => Math.abs(b.avgDailyDelta) - Math.abs(a.avgDailyDelta))
    .slice(0, 5)
}

export function FastestSelling({ forecasts, isLoading }: FastestSellingProps) {
  const topItems = useMemo(() => buildTopItems(forecasts), [forecasts])
  const maxDemand = topItems[0] ? Math.abs(topItems[0].avgDailyDelta) : 1

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Fastest Selling Items</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-4 w-36" />
                    <Skeleton className="h-5 w-16" />
                  </div>
                  <Skeleton className="h-4 w-20" />
                </div>
                <Skeleton className="h-1.5 w-full" />
              </div>
            ))}
          </div>
        ) : topItems.length === 0 ? (
          <p className="text-sm text-muted-foreground">No demand data yet.</p>
        ) : (
          topItems.map((item) => {
            const demandPerDay = Math.abs(item.avgDailyDelta)
            const barWidth = (demandPerDay / maxDemand) * 100
            return (
              <div key={item.id} className="space-y-1.5">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="font-medium truncate">{item.itemName}</span>
                    <Badge variant="outline" className="text-xs shrink-0">
                      {item.itemSku}
                    </Badge>
                  </div>
                  <span className="text-sm text-muted-foreground tabular-nums shrink-0">
                    {demandPerDay.toFixed(1)} units/day
                  </span>
                </div>
                <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
                  <div
                    className="h-full rounded-full bg-primary transition-all"
                    style={{ width: `${barWidth}%` }}
                  />
                </div>
              </div>
            )
          })
        )}
      </CardContent>
    </Card>
  )
}
