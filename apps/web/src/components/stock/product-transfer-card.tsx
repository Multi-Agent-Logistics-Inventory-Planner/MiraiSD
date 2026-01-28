"use client";

import { useState } from "react";
import Image from "next/image";
import { ImageOff, Minus, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
  type Inventory,
} from "@/types/api";

interface ProductTransferCardProps {
  inventory: Inventory;
  transferQuantity: number;
  onQuantityChange: (qty: number) => void;
  maxQuantity: number;
}

export function ProductTransferCard({
  inventory,
  transferQuantity,
  onQuantityChange,
  maxQuantity,
}: ProductTransferCardProps) {
  const [imageError, setImageError] = useState(false);
  const item = inventory.item;

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

  const hasImage = item.imageUrl && !imageError;
  const categoryLabel = PRODUCT_CATEGORY_LABELS[item.category];
  const subcategoryLabel = item.subcategory
    ? PRODUCT_SUBCATEGORY_LABELS[item.subcategory]
    : null;

  return (
    <div className="flex items-center gap-2 sm:gap-4 py-3 sm:py-4 border-b last:border-b-0">
      <div className="relative h-12 w-12 sm:h-20 sm:w-20 flex-shrink-0 rounded-lg overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={item.imageUrl!}
            alt={item.name}
            fill
            sizes="(max-width: 640px) 48px, 80px"
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <p className="font-medium text-xs sm:text-base truncate">{item.name}</p>
        <div className="flex flex-wrap gap-1 mt-1">
          <Badge variant="secondary" className="text-[10px] sm:text-xs px-1.5 py-0">
            {categoryLabel}
          </Badge>
          {subcategoryLabel ? (
            <Badge variant="outline" className="text-[10px] sm:text-xs px-1.5 py-0">
              {subcategoryLabel}
            </Badge>
          ) : null}
        </div>
        <p className="text-[10px] sm:text-xs text-muted-foreground mt-1 sm:hidden">
          Available: {maxQuantity}
        </p>
      </div>

      <div className="flex flex-col items-end gap-1">
        <div className="flex items-center gap-0.5 sm:gap-1">
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-6 w-6 sm:h-7 sm:w-7"
            onClick={handleDecrement}
            disabled={transferQuantity <= 0}
            aria-label={`Decrease quantity for ${item.name}`}
          >
            <Minus className="h-2.5 w-2.5 sm:h-3 sm:w-3" />
          </Button>
          <Input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            value={transferQuantity}
            onChange={(e) => handleInputChange(e.target.value)}
            className="h-6 w-10 sm:h-7 sm:w-14 text-center text-xs sm:text-sm"
            aria-label={`Transfer quantity for ${item.name}`}
          />
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-6 w-6 sm:h-7 sm:w-7"
            onClick={handleIncrement}
            disabled={transferQuantity >= maxQuantity}
            aria-label={`Increase quantity for ${item.name}`}
          >
            <Plus className="h-2.5 w-2.5 sm:h-3 sm:w-3" />
          </Button>
        </div>
        <span className="text-xs text-muted-foreground hidden sm:block">
          Available: {maxQuantity}
        </span>
      </div>
    </div>
  );
}
