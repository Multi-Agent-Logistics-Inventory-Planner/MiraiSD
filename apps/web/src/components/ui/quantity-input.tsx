"use client";

import { useEffect, useState } from "react";
import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export type QuantityIntakeUnit = "pack" | "box";

export interface QuantityIntakeMeta {
  /** The unit the user typed in. "pack" when no toggle is shown. */
  unit: QuantityIntakeUnit;
  /** The raw quantity the user typed in their chosen unit (not multiplied). */
  rawQty: number;
}

interface QuantityInputProps {
  /** Always in packs. Callers see the canonical pack count regardless of toggle state. */
  value: number | "";
  /** Receives the new value in packs. */
  onChange: (value: number | "") => void;
  /** Min in packs. */
  min?: number;
  /** Max in packs. */
  max?: number;
  disabled?: boolean;
  className?: string;
  /** "sm" (default): compact inline widget. "md": larger standalone form field. */
  size?: "sm" | "md";
  /**
   * "joined" (default): -, input, + share one bordered pill.
   * "stacked": detached square buttons, wide input that fills its container,
   * and the Pack/Box toggle drops to a row below.
   */
  layout?: "joined" | "stacked";
  /**
   * Packs per sealed box. When set, a Pack/Box toggle appears next to the input.
   * Selecting "Box" multiplies the user's typed number by this value internally.
   * The component still emits the canonical pack count via onChange.
   */
  packsPerBox?: number | null;
  /**
   * Optional callback for intake metadata. Fires alongside onChange so callers
   * can persist {intakeUnit, intakeQty} into stock-movement metadata for audit
   * log readability ("+2 boxes (72 packs)").
   */
  onIntakeMetaChange?: (meta: QuantityIntakeMeta) => void;
}

export function QuantityInput({
  value,
  onChange,
  min = 0,
  max,
  disabled,
  className,
  packsPerBox,
  onIntakeMetaChange,
  size = "sm",
  layout = "joined",
}: QuantityInputProps) {
  const isStacked = layout === "stacked";
  const btnSizeCls = isStacked ? "h-11 w-11" : size === "md" ? "h-9 w-9" : "h-7 w-7";
  const inputSizeCls = isStacked
    ? "h-11 flex-1 text-base"
    : size === "md"
    ? "h-9 w-14 text-sm"
    : "h-7 w-12 text-xs";
  const hasBoxToggle = !!packsPerBox && packsPerBox > 1;
  const [unit, setUnit] = useState<QuantityIntakeUnit>("pack");

  // Keep the internal unit consistent with whether the toggle is even available.
  // If packsPerBox disappears (product changed), fall back to packs.
  useEffect(() => {
    if (!hasBoxToggle && unit === "box") setUnit("pack");
  }, [hasBoxToggle, unit]);

  const multiplier = unit === "box" && hasBoxToggle ? (packsPerBox as number) : 1;
  const numValue = value === "" ? 0 : value;
  // The displayed raw value reflects what the user typed in their chosen unit.
  const displayedRaw = value === "" ? "" : Math.floor(numValue / multiplier);

  const minRaw = Math.ceil(min / multiplier);
  const maxRaw = max === undefined ? undefined : Math.floor(max / multiplier);

  const canDecrement = (typeof displayedRaw === "number" ? displayedRaw : 0) > minRaw;
  const canIncrement =
    maxRaw === undefined ||
    (typeof displayedRaw === "number" ? displayedRaw : 0) < maxRaw;

  const emit = (rawQty: number | "") => {
    if (rawQty === "") {
      onChange("");
      return;
    }
    const packs = rawQty * multiplier;
    onChange(packs);
    onIntakeMetaChange?.({ unit, rawQty });
  };

  const handleDecrement = () => {
    const cur = typeof displayedRaw === "number" ? displayedRaw : 0;
    const next = cur - 1;
    emit(next <= minRaw ? (minRaw === 0 ? "" : minRaw) : next);
  };

  const handleIncrement = () => {
    const cur = typeof displayedRaw === "number" ? displayedRaw : 0;
    emit(cur + 1);
  };

  const handleUnitChange = (next: QuantityIntakeUnit) => {
    if (next === unit) return;
    setUnit(next);
    // Toggle is a pure display-unit flip — canonical pack count is preserved.
    // The render recomputes displayedRaw against the new multiplier on the next pass.
    if (typeof numValue === "number" && numValue > 0) {
      const newMultiplier = next === "box" && hasBoxToggle ? (packsPerBox as number) : 1;
      const newRaw = Math.floor(numValue / newMultiplier);
      onIntakeMetaChange?.({ unit: next, rawQty: newRaw });
    } else {
      onIntakeMetaChange?.({ unit: next, rawQty: 0 });
    }
  };

  const decrementBtn = (
    <Button
      type="button"
      variant="outline"
      size="icon"
      className={cn(
        btnSizeCls,
        "shrink-0",
        !isStacked && "rounded-r-none border-r-0",
      )}
      onClick={handleDecrement}
      disabled={disabled || !canDecrement}
      aria-label="Decrease quantity"
    >
      <Minus className="h-3 w-3" />
    </Button>
  );

  const incrementBtn = (
    <Button
      type="button"
      variant="outline"
      size="icon"
      className={cn(
        btnSizeCls,
        "shrink-0",
        !isStacked && "rounded-l-none border-l-0",
      )}
      onClick={handleIncrement}
      disabled={disabled || !canIncrement}
      aria-label="Increase quantity"
    >
      <Plus className="h-3 w-3" />
    </Button>
  );

  const inputEl = (
    <Input
      type="text"
      inputMode="numeric"
      pattern="[0-9]*"
      value={displayedRaw}
      placeholder="0"
      onChange={(e) => {
        const raw = e.target.value;
        if (raw === "") {
          emit("");
          return;
        }
        const num = parseInt(raw, 10);
        if (Number.isNaN(num)) return;
        const clamped =
          maxRaw !== undefined ? Math.min(num, maxRaw) : num;
        emit(Math.max(minRaw, clamped));
      }}
      disabled={disabled}
      className={cn(
        inputSizeCls,
        "text-center px-1 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none",
        !isStacked && "rounded-none border-x-0",
      )}
      aria-label="Quantity"
    />
  );

  const toggleEl = hasBoxToggle ? (
    <div
      className={cn(
        "flex items-center text-xs rounded-md border overflow-hidden",
        isStacked && "self-center",
      )}
    >
      <button
        type="button"
        onClick={() => handleUnitChange("pack")}
        disabled={disabled}
        aria-pressed={unit === "pack"}
        className={cn(
          isStacked ? "px-4 py-2 text-sm" : "px-2 py-1",
          "transition-colors disabled:opacity-50",
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
          isStacked ? "px-4 py-2 text-sm" : "px-2 py-1",
          "transition-colors border-l disabled:opacity-50",
          unit === "box"
            ? "bg-foreground text-background"
            : "bg-background text-muted-foreground hover:bg-muted",
        )}
      >
        Box
      </button>
    </div>
  ) : null;

  if (isStacked) {
    return (
      <div className={cn("flex flex-col gap-2", className)}>
        <div className="flex items-center gap-2">
          {decrementBtn}
          {inputEl}
          {incrementBtn}
        </div>
        {toggleEl}
        {hasBoxToggle &&
          unit === "box" &&
          typeof numValue === "number" &&
          numValue > 0 && (
            <span className="text-xs text-muted-foreground self-center">
              = {numValue} packs
            </span>
          )}
      </div>
    );
  }

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div className="flex items-center">
        {decrementBtn}
        {inputEl}
        {incrementBtn}
      </div>
      {toggleEl}
      {hasBoxToggle &&
        unit === "box" &&
        typeof numValue === "number" &&
        numValue > 0 && (
          <span className="text-xs text-muted-foreground whitespace-nowrap">
            = {numValue} packs
          </span>
        )}
    </div>
  );
}
