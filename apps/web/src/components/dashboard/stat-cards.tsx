"use client"

import { DollarSign, AlertTriangle, PackageX, Package, Info } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"

interface StatCardsProps {
  totalStockValue: number
  stockValueChange: number | null
  lowStockItems: number
  criticalItems: number
  outOfStockItems: number
  totalSKUs: number
  isLoading?: boolean
  isError?: boolean
  onRetry?: () => void
}

function KpiTooltip({ text }: { text: string }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
      </TooltipTrigger>
      <TooltipContent>
        <p className="max-w-[200px]">{text}</p>
      </TooltipContent>
    </Tooltip>
  )
}

function StockValueChange({ change }: { change: number | null }) {
  if (change === null) {
    return (
      <p className="text-xs text-muted-foreground">
        <span className="text-muted-foreground">N/A</span>{" "}
        from last month
      </p>
    )
  }
  return (
    <p className="text-xs text-muted-foreground">
      <span className={change >= 0
        ? "text-green-600 dark:text-green-400"
        : "text-red-600 dark:text-red-400"
      }>
        {change >= 0 ? "+" : ""}{change}%
      </span>{" "}
      from last month
    </p>
  )
}

export function StatCards({
  totalStockValue,
  stockValueChange,
  lowStockItems,
  criticalItems,
  outOfStockItems,
  totalSKUs,
  isLoading,
  isError,
  onRetry,
}: StatCardsProps) {
  if (isLoading) {
    return (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={i}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-4 w-4" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-7 w-28" />
              <Skeleton className="mt-2 h-3 w-40" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={i}>
            <CardContent className="pt-6">
              <p className="text-sm text-muted-foreground">Failed to load</p>
              {onRetry && (
                <button
                  onClick={onRetry}
                  className="mt-1 text-xs text-primary underline-offset-2 hover:underline"
                >
                  Retry
                </button>
              )}
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
          <div className="flex items-center gap-1.5">
            <CardTitle className="text-sm font-medium">Total Stock Value</CardTitle>
            <KpiTooltip text="Sum of (quantity x unit cost) across all products" />
          </div>
          <DollarSign className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">
            ${totalStockValue.toLocaleString()}
          </div>
          <StockValueChange change={stockValueChange} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <div className="flex items-center gap-1.5">
            <CardTitle className="text-sm font-medium">Low Stock Items</CardTitle>
            <KpiTooltip text="Items at or approaching their reorder point (within 1.5× threshold)" />
          </div>
          <AlertTriangle className="h-4 w-4 text-amber-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{lowStockItems}</div>
          <p className="text-xs text-muted-foreground">At or approaching reorder level</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <div className="flex items-center gap-1.5">
            <CardTitle className="text-sm font-medium">Critical Items</CardTitle>
            <KpiTooltip text="Items at critical levels or completely out of stock" />
          </div>
          <PackageX className="h-4 w-4 text-red-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-red-600 dark:text-red-400">
            {criticalItems + outOfStockItems}
          </div>
          <p className="text-xs text-muted-foreground">
            {criticalItems} critical, {outOfStockItems} out of stock
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <div className="flex items-center gap-1.5">
            <CardTitle className="text-sm font-medium">Total SKUs</CardTitle>
            <KpiTooltip text="Number of unique products being tracked" />
          </div>
          <Package className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{totalSKUs}</div>
          <p className="text-xs text-muted-foreground">Unique items tracked</p>
        </CardContent>
      </Card>
    </div>
  )
}
