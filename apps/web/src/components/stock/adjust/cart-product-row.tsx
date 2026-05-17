"use client";

import { useState } from "react";
import Image from "next/image";
import { Check, ImageOff, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";
import { QuantityControls } from "./quantity-controls";
import type { AdjustAction, CartLine, NormalizedInventory } from "./types";
import type { QuantityIntakeMeta } from "@/components/ui/quantity-input";

interface CartProductRowProps {
  inventory: NormalizedInventory;
  action: AdjustAction;
  staged: CartLine | null;
  failed: boolean;
  disabled: boolean;
  onStage: () => void;
  onUnstage: () => void;
  onQuantityChange: (value: string) => void;
  onIncrement: () => void;
  onDecrement: () => void;
  onIntakeMetaChange: (meta: QuantityIntakeMeta) => void;
}

export function CartProductRow({
  inventory,
  action,
  staged,
  failed,
  disabled,
  onStage,
  onUnstage,
  onQuantityChange,
  onIncrement,
  onDecrement,
  onIntakeMetaChange,
}: CartProductRowProps) {
  const [imageError, setImageError] = useState(false);
  const item = inventory.item;
  const safeImageUrl = getSafeImageUrl(item.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const isStaged = staged !== null;

  return (
    <div
      className={cn(
        "border-b last:border-b-0 transition-colors",
        isStaged && "bg-primary/5 border-l-2 border-l-primary",
        failed && "bg-rose-500/10 border-l-2 border-l-rose-500"
      )}
    >
      <button
        type="button"
        onClick={isStaged ? onUnstage : onStage}
        disabled={disabled}
        className={cn(
          "w-full flex items-center gap-2 sm:gap-4 py-3 sm:py-4 sm:px-3 text-left",
          "hover:bg-muted/50 transition-colors",
          isStaged && "pl-2",
          disabled && "opacity-50 cursor-not-allowed"
        )}
      >
        <div className="relative h-12 w-12 sm:h-16 sm:w-16 shrink-0 rounded-lg overflow-hidden bg-muted">
          {hasImage ? (
            <Image
              src={safeImageUrl}
              alt={item.name}
              fill
              sizes="48px"
              className="object-cover"
              onError={() => setImageError(true)}
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center">
              <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
            </div>
          )}
          {isStaged && (
            <div className="absolute inset-0 bg-primary/20 flex items-center justify-center">
              <Check className="h-6 w-6 text-primary" />
            </div>
          )}
        </div>

        <div className="w-0 flex-1 overflow-hidden">
          <p className="font-medium text-xs sm:text-base truncate">{item.name}</p>
          <div className="flex flex-wrap gap-1 mt-1">
            <Badge variant="outline" className="text-[10px] sm:text-xs px-1.5 py-0">
              {item.category.name}
            </Badge>
          </div>
        </div>

        <div className="flex flex-col items-end gap-0.5 shrink-0 pl-2">
          <span className="text-sm sm:text-base font-semibold tabular-nums">
            {inventory.quantity}
          </span>
          <span className="text-[10px] sm:text-xs text-muted-foreground">
            in stock
          </span>
        </div>
      </button>

      {isStaged && staged && (
        <div className="flex items-center justify-between gap-3 px-3 pb-3 sm:pb-4 -mt-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onUnstage}
            disabled={disabled}
            className="h-8 text-xs text-muted-foreground hover:text-foreground"
            aria-label="Remove from batch"
          >
            <X className="h-3 w-3 mr-1" />
            Remove
          </Button>
          <QuantityControls
            quantity={staged.quantity}
            currentStock={inventory.quantity}
            action={action}
            disabled={disabled}
            onQuantityChange={onQuantityChange}
            onIncrement={onIncrement}
            onDecrement={onDecrement}
            packsPerBox={item.packsPerBox ?? null}
            onIntakeMetaChange={onIntakeMetaChange}
          />
        </div>
      )}
    </div>
  );
}
