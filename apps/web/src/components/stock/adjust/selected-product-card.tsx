"use client";

import { useState } from "react";
import Image from "next/image";
import { ImageOff, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { PRODUCT_CATEGORY_LABELS, type ProductCategory } from "@/types/api";
import { getSafeImageUrl } from "@/lib/utils/validation";
import { QuantityControls } from "./quantity-controls";
import { AdjustSummary } from "./adjust-summary";
import type { AdjustAction, NormalizedInventory } from "./types";

interface SelectedProductCardProps {
  inventory: NormalizedInventory;
  existingQuantityAtLocation: number;
  action: AdjustAction;
  quantity: number;
  quantityWarning: string | null;
  locationLabel: string;
  disabled: boolean;
  onClearSelection: () => void;
  onQuantityChange: (value: string) => void;
  onIncrement: () => void;
  onDecrement: () => void;
}

export function SelectedProductCard({
  inventory,
  existingQuantityAtLocation,
  action,
  quantity,
  quantityWarning,
  locationLabel,
  disabled,
  onClearSelection,
  onQuantityChange,
  onIncrement,
  onDecrement,
}: SelectedProductCardProps) {
  const [imageError, setImageError] = useState(false);

  const { item } = inventory;
  const safeImageUrl = getSafeImageUrl(item.imageUrl);
  const hasImage = safeImageUrl && !imageError;

  // For subtract: current is the existing quantity at location
  // For add: current is the existing quantity at location (may be 0 if new)
  const currentQty = existingQuantityAtLocation;
  const newQty = action === "subtract" ? currentQty - quantity : currentQty + quantity;

  // For subtract, max is the current stock at location
  // For add, there's no upper limit
  const currentStock = action === "subtract" ? existingQuantityAtLocation : inventory.quantity;

  return (
    <div className="shrink-0 mt-4 space-y-4">
      {/* Header with title and X button */}
      <div className="flex items-center justify-between mt-5">
        <Label className="text-sm text-muted-foreground">Selected Product</Label>
        <button
          type="button"
          onClick={onClearSelection}
          className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted transition-colors cursor-pointer"
          disabled={disabled}
          aria-label="Change product"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Product card */}
      <div className="flex items-center gap-4 bg-muted/50 rounded-xl p-3 mb-8">
        {/* Product image */}
        {hasImage ? (
          <div className="relative h-14 w-14 rounded-lg overflow-hidden shrink-0">
            <Image
              src={safeImageUrl}
              alt={item.name}
              fill
              sizes="56px"
              className="object-cover"
              onError={() => setImageError(true)}
            />
          </div>
        ) : (
          <div className="h-14 w-14 rounded-lg bg-muted shrink-0 flex items-center justify-center">
            <ImageOff className="h-5 w-5 text-muted-foreground" />
          </div>
        )}

        {/* Product name and category */}
        <div className="flex-1 min-w-0">
          <p className="font-medium truncate">{item.name}</p>
          {item.category && (
            <Badge variant="secondary" className="mt-1 text-xs font-normal">
              {PRODUCT_CATEGORY_LABELS[item.category as ProductCategory]}
            </Badge>
          )}
        </div>

        {/* Quantity controls */}
        <QuantityControls
          quantity={quantity}
          currentStock={currentStock}
          action={action}
          disabled={disabled}
          onQuantityChange={onQuantityChange}
          onIncrement={onIncrement}
          onDecrement={onDecrement}
        />
      </div>

      {quantityWarning && (
        <p className="text-xs text-amber-600">{quantityWarning}</p>
      )}

      {/* Summary */}
      <AdjustSummary
        action={action}
        currentQty={currentQty}
        quantity={quantity}
        newQty={newQty}
        locationLabel={locationLabel}
      />
    </div>
  );
}
