"use client";

import { useState } from "react";
import Image from "next/image";
import { ImageOff, Minus, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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

  function handleDecrement() {
    if (transferQuantity > 0) {
      onQuantityChange(transferQuantity - 1);
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

  const hasImage = safeImageUrl && !imageError;
  const categoryLabel = item.category.name;

  return (
    <div className="flex flex-col gap-2 py-3 sm:py-4 border-b last:border-b-0">
      {/* Top row: Image + Name/Category + Available (desktop) */}
      <div className="flex items-center gap-2 sm:gap-4">
        <div className="relative h-12 w-12 sm:h-16 sm:w-16 shrink-0 rounded-lg overflow-hidden bg-muted">
          {hasImage ? (
            <Image
              src={safeImageUrl}
              alt={item.name}
              fill
              sizes="(max-width: 640px) 48px, 64px"
              className="object-cover"
              onError={() => setImageError(true)}
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center">
              <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
            </div>
          )}
        </div>

        <div className="w-0 flex-1 overflow-hidden">
          <p className="font-medium text-xs sm:text-base truncate">{item.name}</p>
          <div className="flex flex-wrap gap-1 mt-1">
            <Badge variant="outline" className="text-[10px] sm:text-xs px-1.5 py-0">
              {categoryLabel}
            </Badge>
          </div>
        </div>

        {/* Available quantity - desktop only, right side */}
        <div className="hidden sm:flex flex-col items-end gap-1 shrink-0">
          <div className="flex items-center gap-1.5">
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="h-8 w-8 touch-manipulation"
              onClick={handleDecrement}
              disabled={disabled || transferQuantity <= 0}
              aria-label={`Decrease quantity for ${item.name}`}
            >
              <Minus className="h-3.5 w-3.5" />
            </Button>
            <Input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              value={transferQuantity}
              onChange={(e) => handleInputChange(e.target.value)}
              disabled={disabled}
              className="h-8 w-14 text-center text-sm touch-manipulation"
              aria-label={`Transfer quantity for ${item.name}`}
            />
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="h-8 w-8 touch-manipulation"
              onClick={handleIncrement}
              disabled={disabled || transferQuantity >= maxQuantity}
              aria-label={`Increase quantity for ${item.name}`}
            >
              <Plus className="h-3.5 w-3.5" />
            </Button>
          </div>
          <span className="text-xs text-muted-foreground">
            Available: {maxQuantity}
          </span>
        </div>
      </div>

      {/* Bottom row: Available + Quantity controls - mobile only */}
      <div className="flex items-center justify-between sm:hidden pl-14">
        <span className="text-[10px] text-muted-foreground">
          Available: {maxQuantity}
        </span>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-9 w-9 touch-manipulation"
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
            className="h-9 w-12 text-center text-sm touch-manipulation"
            aria-label={`Transfer quantity for ${item.name}`}
          />
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-9 w-9 touch-manipulation"
            onClick={handleIncrement}
            disabled={disabled || transferQuantity >= maxQuantity}
            aria-label={`Increase quantity for ${item.name}`}
          >
            <Plus className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
