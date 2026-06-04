"use client";

import { useEffect, useState } from "react";
import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import type {
  QuantityIntakeMeta,
  QuantityIntakeUnit,
} from "@/components/ui/quantity-input";
import type { AdjustAction } from "./types";

interface QuantityControlsProps {
  /** Always in packs. "" represents a staged-but-empty input (no quantity entered yet). */
  quantity: number | "";
  /** Current stock at location (in packs). */
  currentStock: number;
  action: AdjustAction;
  disabled: boolean;
  onQuantityChange: (value: string) => void;
  onIncrement: () => void;
  onDecrement: () => void;
  /** When set and > 1, shows a Pack/Box toggle. The component still emits packs through onQuantityChange. */
  packsPerBox?: number | null;
  /** Callback for intake metadata when the toggle is used. */
  onIntakeMetaChange?: (meta: QuantityIntakeMeta) => void;
}

export function QuantityControls({
  quantity,
  currentStock,
  action,
  disabled,
  onQuantityChange,
  onIncrement,
  onDecrement,
  packsPerBox,
  onIntakeMetaChange,
}: QuantityControlsProps) {
  const hasBoxToggle = !!packsPerBox && packsPerBox > 1;
  const [unit, setUnit] = useState<QuantityIntakeUnit>("pack");

  useEffect(() => {
    if (!hasBoxToggle && unit === "box") setUnit("pack");
  }, [hasBoxToggle, unit]);

  const multiplier = unit === "box" && hasBoxToggle ? (packsPerBox as number) : 1;
  const quantityNum = quantity === "" ? 0 : quantity;
  const displayedRaw = quantity === "" ? "" : Math.floor(quantityNum / multiplier);

  const canDecrement = typeof displayedRaw === "number" && displayedRaw > 1;
  const canIncrement = action === "add" || quantityNum < currentStock;

  const emitRaw = (rawStr: string) => {
    if (rawStr === "") {
      onQuantityChange("");
      return;
    }
    const num = parseInt(rawStr, 10);
    if (Number.isNaN(num)) return;
    const packs = Math.max(1, num) * multiplier;
    onQuantityChange(String(packs));
    onIntakeMetaChange?.({ unit, rawQty: Math.max(1, num) });
  };

  const emitDecrement = () => {
    if (quantity === "") return;
    if (multiplier === 1) {
      onDecrement();
      onIntakeMetaChange?.({ unit: "pack", rawQty: Math.max(1, quantityNum - 1) });
      return;
    }
    if (typeof displayedRaw !== "number") return;
    const nextRaw = Math.max(1, displayedRaw - 1);
    onQuantityChange(String(nextRaw * multiplier));
    onIntakeMetaChange?.({ unit, rawQty: nextRaw });
  };

  const emitIncrement = () => {
    if (multiplier === 1) {
      onIncrement();
      onIntakeMetaChange?.({ unit: "pack", rawQty: quantityNum + 1 });
      return;
    }
    const curRaw = typeof displayedRaw === "number" ? displayedRaw : 0;
    const nextRaw = curRaw + 1;
    onQuantityChange(String(nextRaw * multiplier));
    onIntakeMetaChange?.({ unit, rawQty: nextRaw });
  };

  const handleUnitChange = (next: QuantityIntakeUnit) => {
    if (next === unit) return;
    setUnit(next);
    // Pure display flip — canonical packs preserved; the render recomputes displayedRaw.
    const newMultiplier = next === "box" && hasBoxToggle ? (packsPerBox as number) : 1;
    const newRaw = Math.max(1, Math.floor(quantityNum / newMultiplier));
    onIntakeMetaChange?.({ unit: next, rawQty: newRaw });
  };

  return (
    <div className="shrink-0 flex flex-col items-end gap-1">
      <div className="flex items-center gap-2">
        <div className="flex items-center">
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-9 w-9 rounded-r-none border-r-0 border-input"
            onClick={emitDecrement}
            disabled={disabled || !canDecrement}
            aria-label="Decrease quantity"
          >
            <Minus className="h-4 w-4" />
          </Button>
          <Input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            value={displayedRaw}
            placeholder="0"
            onChange={(e) => emitRaw(e.target.value)}
            disabled={disabled}
            className="h-9 w-14 text-center rounded-none border-x-0"
            aria-label="Quantity to adjust"
          />
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-9 w-9 rounded-l-none border-l-0 border-input"
            onClick={emitIncrement}
            disabled={disabled || !canIncrement}
            aria-label="Increase quantity"
          >
            <Plus className="h-4 w-4" />
          </Button>
        </div>
        {hasBoxToggle && (
          <div className="flex items-center text-xs rounded-md border overflow-hidden">
            <button
              type="button"
              onClick={() => handleUnitChange("pack")}
              disabled={disabled}
              aria-pressed={unit === "pack"}
              className={cn(
                "px-2 py-1 transition-colors disabled:opacity-50",
                unit === "pack"
                  ? "bg-foreground text-background"
                  : "bg-background text-muted-foreground hover:bg-muted",
              )}
            >
              Pack
            </button>
            <button
              type="button"
              onClick={() => handleUnitChange("box")}
              disabled={disabled}
              aria-pressed={unit === "box"}
              className={cn(
                "px-2 py-1 transition-colors border-l disabled:opacity-50",
                unit === "box"
                  ? "bg-foreground text-background"
                  : "bg-background text-muted-foreground hover:bg-muted",
              )}
            >
              Box
            </button>
          </div>
        )}
      </div>
      {hasBoxToggle && unit === "box" && quantityNum > 0 && (
        <span className="text-xs text-muted-foreground">= {quantityNum} packs</span>
      )}
      {action === "subtract" && (
        <span className="text-xs text-muted-foreground">
          Available: {currentStock}
        </span>
      )}
    </div>
  );
}
