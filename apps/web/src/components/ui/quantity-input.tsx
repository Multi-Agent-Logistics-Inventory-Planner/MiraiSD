"use client";

import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface QuantityInputProps {
  value: number | "";
  onChange: (value: number | "") => void;
  min?: number;
  max?: number;
  disabled?: boolean;
  className?: string;
}

export function QuantityInput({
  value,
  onChange,
  min = 0,
  max,
  disabled,
  className,
}: QuantityInputProps) {
  const numValue = value === "" ? 0 : value;
  const canDecrement = numValue > min;
  const canIncrement = max === undefined || numValue < max;

  const handleDecrement = () => {
    const newValue = numValue - 1;
    onChange(newValue <= min ? (min === 0 ? "" : min) : newValue);
  };

  const handleIncrement = () => {
    onChange(numValue + 1);
  };

  return (
    <div className={cn("flex items-center", className)}>
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="h-7 w-7 rounded-r-none border-r-0 shrink-0"
        onClick={handleDecrement}
        disabled={disabled || !canDecrement}
        aria-label="Decrease quantity"
      >
        <Minus className="h-3 w-3" />
      </Button>
      <Input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        value={value}
        placeholder="0"
        onChange={(e) => {
          const raw = e.target.value;
          if (raw === "") {
            onChange("");
          } else {
            const num = parseInt(raw, 10);
            if (!Number.isNaN(num)) {
              const clamped = max !== undefined ? Math.min(num, max) : num;
              onChange(Math.max(min, clamped));
            }
          }
        }}
        disabled={disabled}
        className="h-7 w-10 text-center text-xs rounded-none border-x-0 px-1 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
        aria-label="Quantity"
      />
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="h-7 w-7 rounded-l-none border-l-0 shrink-0"
        onClick={handleIncrement}
        disabled={disabled || !canIncrement}
        aria-label="Increase quantity"
      >
        <Plus className="h-3 w-3" />
      </Button>
    </div>
  );
}
