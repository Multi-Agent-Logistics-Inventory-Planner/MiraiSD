"use client";

import { useState } from "react";
import Image from "next/image";
import { ImageOff, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import {
  QuantityInput,
  type QuantityIntakeMeta,
} from "@/components/ui/quantity-input";
import type { Product, LocationInventory } from "@/types/api";
import { getSafeImageUrl } from "@/lib/utils/validation";

interface SelectedProductProps {
  product: Product;
  existingInventory?: LocationInventory;
  /** In packs (canonical). */
  quantity: string;
  quantityError: string | null;
  /** Receives the new value in packs as a string. */
  onQuantityChange: (value: string) => void;
  onClearSelection: () => void;
  disabled: boolean;
  onIntakeMetaChange?: (meta: QuantityIntakeMeta) => void;
}

export function SelectedProduct({
  product,
  existingInventory,
  quantity,
  quantityError,
  onQuantityChange,
  onClearSelection,
  disabled,
  onIntakeMetaChange,
}: SelectedProductProps) {
  const [imageError, setImageError] = useState(false);

  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const isUpdate = Boolean(existingInventory);

  // QuantityInput holds canonical packs; bridge its number|"" API to our string state.
  const numericValue: number | "" = quantity === "" ? "" : Number(quantity);

  return (
    <div className="shrink-0 space-y-4">
      {/* Header with title and X button */}
      <div className="flex items-center justify-between">
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
      <div className="flex items-center gap-3 sm:gap-4 bg-muted/50 rounded-xl p-3">
        {/* Product image */}
        {hasImage ? (
          <div className="relative h-14 w-14 rounded-lg overflow-hidden shrink-0">
            <Image
              src={safeImageUrl}
              alt={product.name}
              fill
              sizes="48px"
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
          <p className="font-medium truncate">{product.name}</p>
          {product.category && (
            <Badge variant="outline" className="mt-1 text-xs font-normal">
              {product.category.name}
            </Badge>
          )}
        </div>
      </div>

      {/* Info box for existing inventory */}
      {isUpdate && existingInventory && (
        <div className="rounded-md bg-muted p-3 text-sm">
          <p className="text-muted-foreground">
            This product already exists in this location with{" "}
            <span className="font-medium text-foreground">
              {existingInventory.quantity}
            </span>{" "}
            units. Enter the new total quantity below.
          </p>
        </div>
      )}

      {/* Quantity input */}
      <div className="grid gap-2">
        <Label htmlFor="add-qty">
          {isUpdate ? "New Quantity" : "Quantity"}
        </Label>
        <QuantityInput
          value={numericValue}
          onChange={(v) => onQuantityChange(v === "" ? "" : String(v))}
          min={1}
          disabled={disabled}
          packsPerBox={product.packsPerBox ?? null}
          onIntakeMetaChange={onIntakeMetaChange}
        />
        {quantityError && (
          <p className="text-xs text-destructive">{quantityError}</p>
        )}
      </div>
    </div>
  );
}
