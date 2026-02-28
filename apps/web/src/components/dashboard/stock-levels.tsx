"use client"

import { useEffect, useMemo, useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { cn } from "@/lib/utils"
import { Skeleton } from "@/components/ui/skeleton"
import type { StockLevelItem, StockStatus } from "@/types/dashboard"

type SortOption = "days-to-stockout" | "stock-status" | "name-az"

const PAGE_SIZE = 5

interface StockLevelsProps {
  items: StockLevelItem[]
  isLoading?: boolean
}

function getStatusColor(status: StockStatus) {
  switch (status) {
    case "good":
      return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
    case "low":
      return "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400"
    case "critical":
      return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
    case "out-of-stock":
      return "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400"
    default:
      return "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400"
  }
}

function getProgressColor(status: StockStatus) {
  switch (status) {
    case "good":
      return "[&>[data-slot=progress-indicator]]:bg-green-500"
    case "low":
      return "[&>[data-slot=progress-indicator]]:bg-amber-500"
    case "critical":
      return "[&>[data-slot=progress-indicator]]:bg-red-500"
    case "out-of-stock":
      return "[&>[data-slot=progress-indicator]]:bg-gray-400"
    default:
      return ""
  }
}

function formatStatus(status: StockStatus) {
  return status
    .split("-")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ")
}

function formatDaysToStockout(days: number | undefined): string | null {
  if (days === undefined) return null
  if (days <= 0) return "OOS"
  if (days > 365) return ">1yr"
  return `${Math.round(days)}d`
}

const STATUS_RANK: Record<StockStatus, number> = {
  "out-of-stock": 0,
  critical: 1,
  low: 2,
  good: 3,
}

function sortItems(items: StockLevelItem[], sort: SortOption): StockLevelItem[] {
  const copy = items.slice()
  switch (sort) {
    case "days-to-stockout":
      return copy.sort((a, b) => {
        const da = a.daysToStockout ?? Infinity
        const db = b.daysToStockout ?? Infinity
        return da - db
      })
    case "stock-status":
      return copy.sort((a, b) => {
        const ra = STATUS_RANK[a.status]
        const rb = STATUS_RANK[b.status]
        if (ra !== rb) return ra - rb
        return a.stock - b.stock
      })
    case "name-az":
      return copy.sort((a, b) => a.name.localeCompare(b.name))
    default:
      return copy
  }
}

export function StockLevels({ items, isLoading }: StockLevelsProps) {
  const [sort, setSort] = useState<SortOption>("days-to-stockout")
  const [page, setPage] = useState(0)

  const sorted = useMemo(() => sortItems(items, sort), [items, sort])
  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
  const paged = useMemo(
    () => sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE),
    [sorted, page]
  )

  useEffect(() => {
    setPage(0)
  }, [sort])

  return (
    <Card className="flex flex-col h-full">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Real-Time Stock Levels</CardTitle>
        <Select value={sort} onValueChange={(v) => setSort(v as SortOption)}>
          <SelectTrigger className="w-44 h-8 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="days-to-stockout">Days to Stockout</SelectItem>
            <SelectItem value="stock-status">Stock Status</SelectItem>
            <SelectItem value="name-az">Name A-Z</SelectItem>
          </SelectContent>
        </Select>
      </CardHeader>
      <CardContent className="flex flex-col flex-1 min-h-0">
        {isLoading ? (
          <div className="flex flex-col justify-between flex-1">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-4 w-40" />
                    <Skeleton className="h-5 w-20" />
                  </div>
                  <Skeleton className="h-4 w-16" />
                </div>
                <Skeleton className="h-2 w-full" />
                <Skeleton className="h-3 w-32" />
              </div>
            ))}
          </div>
        ) : items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No inventory data yet.</p>
        ) : (
          <div className="flex flex-col justify-between flex-1">
            {paged.map((item) => {
              const daysLabel = formatDaysToStockout(item.daysToStockout)
              return (
                <div key={item.itemId} className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{item.name}</span>
                      <Badge variant="outline" className={cn("text-xs", getStatusColor(item.status))}>
                        {formatStatus(item.status)}
                      </Badge>
                      {daysLabel !== null && item.status !== "out-of-stock" && (
                        <Badge variant="secondary" className="text-xs tabular-nums">
                          {daysLabel}
                        </Badge>
                      )}
                    </div>
                    <span className="text-sm text-muted-foreground">
                      {item.stock} units
                    </span>
                  </div>
                  <Progress
                    value={(item.stock / item.maxStock) * 100}
                    className={cn("h-2", getProgressColor(item.status))}
                  />
                  <p className="text-xs text-muted-foreground">
                    Last updated: {item.lastUpdated}
                  </p>
                </div>
              )
            })}
            {totalPages > 1 && (
              <div className="flex items-center justify-between border-t pt-3">
                <span className="text-xs text-muted-foreground">
                  {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, sorted.length)} of {sorted.length}
                </span>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => setPage((p) => p - 1)}
                    disabled={page === 0}
                    aria-label="Previous page"
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-xs text-muted-foreground tabular-nums">
                    {page + 1} / {totalPages}
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={page >= totalPages - 1}
                    aria-label="Next page"
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
