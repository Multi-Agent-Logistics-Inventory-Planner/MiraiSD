"use client";

import { ArrowRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { AdjustAction } from "./types";

interface AdjustSummaryProps {
  action: AdjustAction;
  currentQty: number;
  quantity: number;
  newQty: number;
  locationLabel: string;
}

export function AdjustSummary({
  action,
  currentQty,
  quantity,
  newQty,
  locationLabel,
}: AdjustSummaryProps) {
  if (quantity <= 0) {
    return null;
  }

  const isSubtract = action === "subtract";

  return (
    <div
      className={cn(
        "rounded-xl border p-4",
        isSubtract
          ? "bg-amber-50 border-amber-200 dark:bg-amber-950/20 dark:border-amber-900"
          : "bg-emerald-50 border-emerald-200 dark:bg-emerald-950/20 dark:border-emerald-900"
      )}
    >
      <div className="flex items-center justify-between gap-4">
        {/* Before */}
        <div className="flex-1 text-center">
          <p className="text-xs text-muted-foreground mb-1">Current</p>
          <p className="text-2xl font-semibold tabular-nums">{currentQty}</p>
        </div>

        {/* Arrow with change badge */}
        <div className="flex flex-col items-center gap-1">
          <Badge
            variant="secondary"
            className={cn(
              "text-xs font-medium",
              isSubtract
                ? "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300"
                : "bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300"
            )}
          >
            {isSubtract ? `-${quantity}` : `+${quantity}`}
          </Badge>
          <ArrowRight
            className={cn(
              "h-5 w-5",
              isSubtract
                ? "text-amber-500 dark:text-amber-400"
                : "text-emerald-500 dark:text-emerald-400"
            )}
          />
        </div>

        {/* After */}
        <div className="flex-1 text-center">
          <p className="text-xs text-muted-foreground mb-1">After</p>
          <p
            className={cn(
              "text-2xl font-bold tabular-nums",
              isSubtract
                ? "text-amber-700 dark:text-amber-400"
                : "text-emerald-700 dark:text-emerald-400"
            )}
          >
            {newQty}
          </p>
        </div>
      </div>

      {/* Location context */}
      <p className="text-xs text-muted-foreground text-center mt-3 pt-3 border-t border-current/10">
        {isSubtract ? "Removing from" : "Adding to"}{" "}
        <span className="font-medium text-foreground">{locationLabel}</span>
      </p>
    </div>
  );
}
