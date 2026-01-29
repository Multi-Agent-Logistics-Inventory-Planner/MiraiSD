"use client";

import { useState } from "react";
import Image from "next/image";
import { Check, ImageOff } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
  type Inventory,
} from "@/types/api";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";

interface ProductSelectCardProps {
  inventory: Inventory;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
}

export function ProductSelectCard({
  inventory,
  selected,
  onSelect,
  disabled = false,
}: ProductSelectCardProps) {
  const [imageError, setImageError] = useState(false);
  const item = inventory.item;

  const safeImageUrl = getSafeImageUrl(item.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const categoryLabel = PRODUCT_CATEGORY_LABELS[item.category];
  const subcategoryLabel = item.subcategory
    ? PRODUCT_SUBCATEGORY_LABELS[item.subcategory]
    : null;

  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={disabled}
      className={cn(
        "w-full flex items-center gap-2 sm:gap-4 py-3 sm:py-4 sm:px-3 border-b last:border-b-0 cursor-pointer",
        "transition-colors text-left",
        selected
          ? "bg-primary/5 border-l-2 border-l-primary pl-2"
          : "hover:bg-muted/50",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-12 w-12 sm:h-16 sm:w-16 flex-shrink-0 rounded-lg overflow-hidden bg-muted">
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
        {selected && (
          <div className="absolute inset-0 bg-primary/20 flex items-center justify-center">
            <Check className="h-6 w-6 text-primary" />
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
      </div>

      <div className="flex flex-col items-center gap-1 shrink-0">
        <span className="text-sm font-semibold tabular-nums">
          {inventory.quantity}
        </span>
        <span className="text-[10px] sm:text-xs text-muted-foreground">
          in stock
        </span>
      </div>
    </button>
  );
}
