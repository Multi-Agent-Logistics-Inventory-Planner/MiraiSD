"use client";

import { cn } from "@/lib/utils";
import { StockMovementReason } from "@/types/api";
import { ReasonSelector } from "./reason-selector";
import type { AdjustAction } from "./types";

interface CartSummaryStripProps {
  itemCount: number;
  totalQuantity: number;
  action: AdjustAction;
  reason: StockMovementReason;
  disabled: boolean;
  onReasonChange: (reason: StockMovementReason) => void;
}

export function CartSummaryStrip({
  itemCount,
  totalQuantity,
  action,
  reason,
  disabled,
  onReasonChange,
}: CartSummaryStripProps) {
  const hasItems = itemCount > 0;
  const signedTotal = action === "subtract" ? -totalQuantity : totalQuantity;
  const signLabel = signedTotal >= 0 ? `+${signedTotal}` : `${signedTotal}`;

  return (
    <div
      className={cn(
        "overflow-hidden transition-all duration-200 ease-in-out",
        hasItems ? "max-h-48 opacity-100" : "max-h-0 opacity-0"
      )}
      aria-live="polite"
      aria-atomic="true"
    >
      <div className="px-6 pt-4 space-y-3">
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            {itemCount === 1 ? "1 item staged" : `${itemCount} items staged`}
          </span>
          <span
            className={cn(
              "font-medium tabular-nums",
              action === "subtract"
                ? "text-rose-600 dark:text-amber-500"
                : "text-emerald-700 dark:text-emerald-500"
            )}
          >
            total {signLabel} units
          </span>
        </div>
        <ReasonSelector
          action={action}
          value={reason}
          onChange={onReasonChange}
          disabled={disabled}
        />
      </div>
    </div>
  );
}
