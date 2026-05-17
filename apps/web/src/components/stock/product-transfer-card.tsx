"use client";

import { useState } from "react";
import Image from "next/image";
import { Check, ImageOff, Minus, Plus, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import type { LocationInventory } from "@/types/api";
import { getSafeImageUrl } from "@/lib/utils/validation";

interface ProductTransferCardProps {
  inventory: LocationInventory;
  transferQuantity: number;
  onQuantityChange: (qty: number) => void;
  maxQuantity: number;
  disabled?: boolean;
}

export function ProductTransferCard({
  inventory,
  transferQuantity,
  onQuantityChange,
  maxQuantity,
  disabled = false,
}: ProductTransferCardProps) {
  const [imageError, setImageError] = useState(false);
  const item = inventory.item;
  const safeImageUrl = getSafeImageUrl(item.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const isStaged = transferQuantity > 0;

  function handleStageToggle() {
    if (disabled) return;
    if (isStaged) {
      onQuantityChange(0);
    } else {
      onQuantityChange(Math.min(1, maxQuantity));
    }
  }

  function handleDecrement() {
    if (transferQuantity > 1) {
      onQuantityChange(transferQuantity - 1);
    } else {
      onQuantityChange(0);
    }
  }

  function handleIncrement() {
    if (transferQuantity < maxQuantity) {
      onQuantityChange(transferQuantity + 1);
    }
  }

  function handleInputChange(value: string) {
    const trimmed = value.trim();
    if (trimmed === "" || !/^\d+$/.test(trimmed)) {
      onQuantityChange(0);
      return;
    }
    const parsed = parseInt(trimmed, 10);
    const clamped = Math.max(0, Math.min(parsed, maxQuantity));
    onQuantityChange(clamped);
  }

  function handleRemove(e: React.MouseEvent) {
    e.stopPropagation();
    onQuantityChange(0);
  }

  return (
    <div
      className={cn(
        "border-b last:border-b-0 transition-colors",
        isStaged && "bg-primary/5 border-l-2 border-l-primary"
      )}
    >
      <button
        type="button"
        onClick={handleStageToggle}
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
            {maxQuantity}
          </span>
          <span className="text-[10px] sm:text-xs text-muted-foreground">
            in stock
          </span>
        </div>
      </button>

      {isStaged && (
        <div className="flex items-center justify-between gap-3 px-3 pb-3 sm:pb-4 -mt-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleRemove}
            disabled={disabled}
            className="h-8 text-xs text-muted-foreground hover:text-foreground"
            aria-label={`Remove ${item.name} from transfer`}
          >
            <X className="h-3 w-3 mr-1" />
            Remove
          </Button>
          <div className="shrink-0 flex flex-col items-end gap-1">
            <div className="flex items-center">
              <Button
                type="button"
                variant="outline"
                size="icon"
                className="h-9 w-9 rounded-r-none border-r-0 border-input touch-manipulation"
                onClick={handleDecrement}
                disabled={disabled || transferQuantity <= 0}
                aria-label={`Decrease quantity for ${item.name}`}
              >
                <Minus className="h-4 w-4" />
              </Button>
              <Input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                value={transferQuantity}
                onChange={(e) => handleInputChange(e.target.value)}
                disabled={disabled}
                className="h-9 w-14 text-center rounded-none border-x-0 touch-manipulation"
                aria-label={`Transfer quantity for ${item.name}`}
              />
              <Button
                type="button"
                variant="outline"
                size="icon"
                className="h-9 w-9 rounded-l-none border-l-0 border-input touch-manipulation"
                onClick={handleIncrement}
                disabled={disabled || transferQuantity >= maxQuantity}
                aria-label={`Increase quantity for ${item.name}`}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
            <span className="text-xs text-muted-foreground">
              Available: {maxQuantity}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
