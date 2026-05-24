"use client";

import { cn } from "@/lib/utils";
import { StockMovementReason } from "@/types/api";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { REASON_OPTIONS_BY_ACTION, type AdjustAction } from "./types";

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
  const options = REASON_OPTIONS_BY_ACTION[action];

  return (
    <div
      className={cn(
        "overflow-hidden transition-all duration-200 ease-in-out",
        hasItems ? "max-h-32 opacity-100" : "max-h-0 opacity-0"
      )}
      aria-live="polite"
      aria-atomic="true"
    >
      <div className="px-6 pt-2 pb-1 space-y-1">
        <div className="flex items-center justify-between">
          <Label className="text-sm text-muted-foreground">Reason</Label>
          <span
            className={cn(
              "text-sm font-medium tabular-nums",
              action === "subtract"
                ? "text-rose-600 dark:text-amber-500"
                : "text-emerald-700 dark:text-emerald-500"
            )}
          >
            {signLabel} units
          </span>
        </div>
        <Select
          value={reason}
          onValueChange={(v) => onReasonChange(v as StockMovementReason)}
          disabled={disabled}
        >
          <SelectTrigger className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {options.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
