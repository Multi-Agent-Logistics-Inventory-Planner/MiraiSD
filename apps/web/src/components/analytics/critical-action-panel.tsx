"use client"

import { useState } from "react"
import { AlertTriangle, ChevronDown, ChevronUp, ExternalLink } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import { Skeleton } from "@/components/ui/skeleton"
import type { ForecastPrediction } from "@/types/api"

interface CriticalActionPanelProps {
  criticalItems: ForecastPrediction[]
  isLoading?: boolean
  onViewCritical?: () => void
}

export function CriticalActionPanel({
  criticalItems,
  isLoading,
  onViewCritical,
}: CriticalActionPanelProps) {
  const [isOpen, setIsOpen] = useState(true)

  if (isLoading) {
    return (
      <Skeleton className="h-20 w-full" />
    )
  }

  if (criticalItems.length === 0) {
    return null
  }

  const totalReorderQty = criticalItems.reduce(
    (sum, item) => sum + item.suggestedReorderQty,
    0
  )

  const totalReorderValue = criticalItems.reduce(
    (sum, item) => sum + (item.suggestedReorderQty * (item.unitCost ?? 0)),
    0
  )

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <div className="w-full rounded-lg border border-red-200 bg-red-50 p-4 text-sm dark:border-red-900 dark:bg-red-950">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-red-800 dark:text-red-200">
            <AlertTriangle className="h-4 w-4 shrink-0 text-red-600" />
            <span className="font-semibold">Immediate Action Required</span>
          </div>
          <CollapsibleTrigger asChild>
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              aria-label={isOpen ? "Collapse critical items" : "Expand critical items"}
              aria-expanded={isOpen}
            >
              {isOpen ? (
                <ChevronUp className="h-4 w-4" />
              ) : (
                <ChevronDown className="h-4 w-4" />
              )}
            </Button>
          </CollapsibleTrigger>
        </div>

        <div className="mt-2 flex flex-col gap-2 text-red-700 sm:flex-row sm:items-center sm:justify-between dark:text-red-300">
          <div>
            <span className="font-semibold">{criticalItems.length} item{criticalItems.length !== 1 ? "s" : ""}</span>
            {" "}predicted to run out within 3 days.
            {totalReorderValue > 0 && (
              <span className="ml-1">
                Estimated reorder cost: <span className="font-semibold">${totalReorderValue.toLocaleString()}</span>
              </span>
            )}
          </div>
          {onViewCritical && (
            <Button
              variant="outline"
              size="sm"
              className="border-red-300 text-red-700 hover:bg-red-100 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-900"
              onClick={onViewCritical}
            >
              View Critical Items
              <ExternalLink className="ml-2 h-3 w-3" />
            </Button>
          )}
        </div>

        <CollapsibleContent className="mt-3">
          <div className="rounded-md border border-red-200 bg-white overflow-hidden dark:border-red-800 dark:bg-red-950">
            <div className="max-h-72 overflow-y-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-red-100 dark:bg-red-900">
                  <tr>
                    <th className="px-3 py-2 text-left font-medium">Item</th>
                    <th className="px-3 py-2 text-right font-medium">Days Left</th>
                    <th className="px-3 py-2 text-right font-medium">Reorder Qty</th>
                  </tr>
                </thead>
                <tbody>
                  {criticalItems.map((item) => (
                    <tr key={item.id} className="border-t border-red-100 dark:border-red-800">
                      <td className="px-3 py-2">
                        <div className="font-medium">{item.itemName}</div>
                        <div className="text-xs text-muted-foreground">{item.itemSku}</div>
                      </td>
                      <td className="px-3 py-2 text-right">
                        <span className="font-bold text-red-600">{item.daysToStockout}</span>
                      </td>
                      <td className="px-3 py-2 text-right">
                        {item.suggestedReorderQty} units
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="border-t border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900">
                  <tr>
                    <td className="px-3 py-2 font-medium">Total</td>
                    <td className="px-3 py-2"></td>
                    <td className="px-3 py-2 text-right font-medium">{totalReorderQty} units</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        </CollapsibleContent>
      </div>
    </Collapsible>
  )
}
