"use client"

import { useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  DataTableHeader,
  TableHead,
  TableRow,
} from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import type { ForecastPrediction } from "@/types/api"

interface TabTopSellersProps {
  forecasts: ForecastPrediction[]
  isLoadingForecasts: boolean
}

export function TabTopSellers({
  forecasts,
  isLoadingForecasts,
}: TabTopSellersProps) {
  const ranked = useMemo(() => {
    return [...forecasts]
      .sort((a, b) => Math.abs(b.avgDailyDelta) - Math.abs(a.avgDailyDelta))
      .slice(0, 50)
  }, [forecasts])

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Top Items by Daily Demand</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoadingForecasts ? (
            <div className="space-y-2">
              {Array.from({ length: 8 }).map((_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : ranked.length === 0 ? (
            <div className="flex h-[150px] items-center justify-center text-muted-foreground">
              No forecast data available
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <DataTableHeader>
                  <TableHead className="rounded-l-lg w-12">#</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead className="text-right">Current Stock</TableHead>
                  <TableHead className="text-right">Daily Demand</TableHead>
                  <TableHead className="rounded-r-lg text-right">Est. Monthly</TableHead>
                </DataTableHeader>
                <TableBody>
                  {ranked.map((item, index) => {
                    const dailyDemand = Math.abs(item.avgDailyDelta)
                    const estMonthly = Math.round(dailyDemand * 30)
                    return (
                      <TableRow key={item.id}>
                        <TableCell className="rounded-l-lg text-muted-foreground tabular-nums">
                          {index + 1}
                        </TableCell>
                        <TableCell className="font-mono text-sm">
                          {item.itemSku ?? "-"}
                        </TableCell>
                        <TableCell className="max-w-[200px] truncate font-medium">
                          {item.itemName}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {item.currentStock}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {dailyDemand.toFixed(1)}/day
                        </TableCell>
                        <TableCell className="rounded-r-lg text-right tabular-nums">
                          {estMonthly} units
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
