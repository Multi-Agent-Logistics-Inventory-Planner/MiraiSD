"use client";

import { useState } from "react";
import Image from "next/image";
import { Check, ImageOff } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { Product } from "@/types/api";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";

interface ProductCardProps {
  product: Product;
  existingQuantity?: number;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
}

export function ProductCard({
  product,
  existingQuantity,
  selected,
  onSelect,
  disabled = false,
}: ProductCardProps) {
  const [imageError, setImageError] = useState(false);

  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const categoryLabel = product.category.name;
  const hasExisting = existingQuantity !== undefined && existingQuantity > 0;

  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={disabled}
      className={cn(
        "overflow-hidden min-w-0 w-full flex items-center gap-2 sm:gap-4 py-3 sm:py-4 sm:px-3 border-b last:border-b-0 cursor-pointer",
        "transition-colors text-left",
        selected
          ? "bg-primary/5 border-l-2 border-l-primary pl-2"
          : "hover:bg-muted/50",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-12 w-12 sm:h-16 sm:w-16 shrink-0 rounded-lg overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={safeImageUrl}
            alt={product.name}
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

      <div className="w-0 flex-1 overflow-hidden">
        <p className="font-medium text-xs sm:text-base truncate">{product.name}</p>
        <div className="flex flex-wrap gap-1 mt-1">
          <Badge variant="outline" className="text-[10px] sm:text-xs px-1.5 py-0">
            {categoryLabel}
          </Badge>
        </div>
      </div>

      <div className="flex flex-col items-end gap-0.5 shrink-0 pl-2">
        {hasExisting ? (
          <>
            <span className="text-sm sm:text-base font-semibold tabular-nums">
              {existingQuantity}
            </span>
            <span className="text-[10px] sm:text-xs text-muted-foreground">
              in stock
            </span>
          </>
        ) : (
          <Badge variant="secondary" className="text-[10px] sm:text-xs">
            New
          </Badge>
        )}
      </div>
    </button>
  );
}
