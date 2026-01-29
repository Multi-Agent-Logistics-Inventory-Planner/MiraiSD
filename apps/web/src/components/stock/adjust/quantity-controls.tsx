"use client";

import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { AdjustAction } from "./types";

interface QuantityControlsProps {
  quantity: number;
  currentStock: number;
  action: AdjustAction;
  disabled: boolean;
  onQuantityChange: (value: string) => void;
  onIncrement: () => void;
  onDecrement: () => void;
}

export function QuantityControls({
  quantity,
  currentStock,
  action,
  disabled,
  onQuantityChange,
  onIncrement,
  onDecrement,
}: QuantityControlsProps) {
  const canDecrement = quantity > 1;
  const canIncrement = action === "add" || quantity < currentStock;

  return (
    <div className="shrink-0 flex flex-col items-end gap-1">
      <div className="flex items-center">
        <Button
          type="button"
          variant="outline"
          size="icon"
          className="h-9 w-9 rounded-r-none border-r-0"
          onClick={onDecrement}
          disabled={disabled || !canDecrement}
          aria-label="Decrease quantity"
        >
          <Minus className="h-4 w-4" />
        </Button>
        <Input
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          value={quantity}
          onChange={(e) => onQuantityChange(e.target.value)}
          disabled={disabled}
          className="h-9 w-14 text-center rounded-none border-x-0"
          aria-label="Quantity to adjust"
        />
        <Button
          type="button"
          variant="outline"
          size="icon"
          className="h-9 w-9 rounded-l-none border-l-0"
          onClick={onIncrement}
          disabled={disabled || !canIncrement}
          aria-label="Increase quantity"
        >
          <Plus className="h-4 w-4" />
        </Button>
      </div>
      {action === "subtract" && (
        <span className="text-xs text-muted-foreground">
          Available: {currentStock}
        </span>
      )}
    </div>
  );
}
