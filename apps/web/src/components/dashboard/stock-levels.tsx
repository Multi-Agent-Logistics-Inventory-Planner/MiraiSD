"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Badge } from "@/components/ui/badge"
import type { InventoryItem } from "@/lib/data"
import { cn } from "@/lib/utils"

interface StockLevelsProps {
  items: InventoryItem[]
}

function getStatusColor(status: InventoryItem["status"]) {
  switch (status) {
    case "good":
      return "bg-green-100 text-green-700"
    case "low":
      return "bg-amber-100 text-amber-700"
    case "critical":
      return "bg-red-100 text-red-700"
    case "out-of-stock":
      return "bg-gray-100 text-gray-700"
    default:
      return "bg-gray-100 text-gray-700"
  }
}

function getProgressColor(status: InventoryItem["status"]) {
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

function formatStatus(status: InventoryItem["status"]) {
  return status
    .split("-")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ")
}

export function StockLevels({ items }: StockLevelsProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Real-Time Stock Levels</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {items.slice(0, 5).map((item) => (
          <div key={item.id} className="space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="font-medium">{item.name}</span>
                <Badge variant="outline" className={cn("text-xs", getStatusColor(item.status))}>
                  {formatStatus(item.status)}
                </Badge>
              </div>
              <span className="text-sm text-muted-foreground">
                {item.stock}/{item.maxStock}
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
        ))}
      </CardContent>
    </Card>
  )
}
