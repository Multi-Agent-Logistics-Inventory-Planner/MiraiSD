"use client"

import { useState } from "react"
import { AlertTriangle, ChevronDown, ChevronUp, ExternalLink } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
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
      <Alert className="border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950">
        <AlertTriangle className="h-4 w-4 text-red-600" />
        <AlertTitle className="flex items-center justify-between text-red-800 dark:text-red-200">
          <span>Immediate Action Required</span>
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
        </AlertTitle>
        <AlertDescription className="text-red-700 dark:text-red-300">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
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
            <div className="rounded-md border border-red-200 bg-white dark:border-red-800 dark:bg-red-950">
              <div className="max-h-40 overflow-y-auto">
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
        </AlertDescription>
      </Alert>
    </Collapsible>
  )
}
