"use client";

import { useState } from "react";
import { ChevronsUpDown, Loader2, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ImageUpload } from "@/components/ui/image-upload";
import { ProductLocationSelector } from "@/components/stock/product-location-selector";
import { SelectShipmentProductDialog } from "@/components/shipments/select-shipment-product-dialog";
import { cn } from "@/lib/utils";
import { validateFile } from "@/lib/supabase/storage";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import type { ProductListItem } from "@/types/api";
import { EMPTY_LOCATION, type DraftTier } from "@/components/kuji/tier-draft";

export interface TierSidebarItemProps {
  readonly tier: DraftTier;
  readonly index: number;
  readonly active: boolean;
  readonly canRemove: boolean;
  readonly disabled: boolean;
  readonly onSelect: () => void;
  readonly onRemove: () => void;
}

export function TierSidebarItem({
  tier,
  index,
  active,
  canRemove,
  disabled,
  onSelect,
  onRemove,
}: TierSidebarItemProps) {
  const subtitle =
    tier.mode === "existing"
      ? tier.linkedProductDisplayName || "No product picked"
      : tier.productName || "No name set";
  const slipsText =
    tier.count === "" || tier.count === 0
      ? "No slips set"
      : `${tier.count} slips`;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect();
        }
      }}
      className={cn(
        "group flex items-center gap-2 px-2 py-2 rounded-md cursor-pointer mb-0.5 border border-transparent transition-colors",
        active
          ? "bg-background border-border shadow-sm"
          : "hover:bg-muted/60",
      )}
    >
      <div
        className={cn(
          "w-7 h-7 rounded-md flex items-center justify-center text-xs font-semibold shrink-0",
          active
            ? "bg-muted text-foreground"
            : "bg-muted/60 text-muted-foreground",
        )}
      >
        {index + 1}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-xs font-medium truncate">
          {tier.label || "Untitled tier"}
        </div>
        <div className="text-[11px] text-muted-foreground truncate">
          {subtitle} · {slipsText}
        </div>
      </div>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onRemove();
        }}
        disabled={disabled || !canRemove}
        aria-label={`Remove tier ${index + 1}`}
        className={cn(
          "shrink-0 p-1 rounded text-muted-foreground hover:text-destructive hover:bg-destructive/10 opacity-0 group-hover:opacity-100 transition-opacity",
          (!canRemove || disabled) && "pointer-events-none opacity-0",
        )}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

export interface TierPanelProps {
  readonly tier: DraftTier;
  readonly index: number;
  readonly errors: Record<string, string>;
  readonly isPending: boolean;
  readonly onUpdate: (tempId: string, updates: Partial<DraftTier>) => void;
  readonly onLinkedProductSelect: (tempId: string, product: ProductListItem) => void;
}

export function TierPanel({
  tier,
  index,
  errors,
  isPending,
  onUpdate,
  onLinkedProductSelect,
}: TierPanelProps) {
  const [productPickerOpen, setProductPickerOpen] = useState(false);
  const inventoryQuery = useProductInventoryEntries(
    tier.linkedProductId || null,
  );
  const availableEntries = (inventoryQuery.data?.entries ?? []).filter(
    (e) => e.locationId !== null && e.quantity > 0,
  );

  return (
    <div className="p-5 space-y-4">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        Tier
        <span className="bg-muted rounded px-2 py-0.5 text-[11px] text-foreground">
          {index + 1}
        </span>
      </div>

      <div className="grid gap-1.5">
        <Label className="text-xs">Label</Label>
        <Input
          value={tier.label}
          onChange={(e) => onUpdate(tier.tempId, { label: e.target.value })}
          placeholder="e.g. S, A, Last One…"
          disabled={isPending}
        />
        {errors[`tier:${tier.tempId}:label`] ? (
          <p className="text-xs text-destructive">
            {errors[`tier:${tier.tempId}:label`]}
          </p>
        ) : null}
      </div>

      <div className="grid gap-1.5">
        <Label className="text-xs">Prize source</Label>
        <div className="grid grid-cols-2 gap-1 rounded-md border p-1 bg-muted/30">
          <button
            type="button"
            className={cn(
              "rounded px-3 py-1.5 text-xs transition-colors",
              tier.mode === "create"
                ? "bg-background font-medium shadow-sm"
                : "text-muted-foreground hover:bg-muted",
            )}
            onClick={() =>
              onUpdate(tier.tempId, {
                mode: "create",
                linkedProductId: "",
                source: EMPTY_LOCATION,
              })
            }
            disabled={isPending}
          >
            Create new
          </button>
          <button
            type="button"
            className={cn(
              "rounded px-3 py-1.5 text-xs transition-colors",
              tier.mode === "existing"
                ? "bg-background font-medium shadow-sm"
                : "text-muted-foreground hover:bg-muted",
            )}
            onClick={() =>
              onUpdate(tier.tempId, {
                mode: "existing",
                productName: "",
                productImageFile: null,
                productImagePreviewUrl: null,
                productImageUrl: "",
              })
            }
            disabled={isPending}
          >
            Existing product
          </button>
        </div>
      </div>

      {tier.mode === "existing" ? (
        <>
          <div className="grid gap-1.5">
            <Label className="text-xs">Linked Product</Label>
            <Button
              type="button"
              variant="outline"
              className="justify-between w-full"
              disabled={isPending}
              onClick={() => setProductPickerOpen(true)}
            >
              {tier.linkedProductId
                ? tier.linkedProductDisplaySku
                  ? `${tier.linkedProductDisplayName} (${tier.linkedProductDisplaySku})`
                  : tier.linkedProductDisplayName || "—"
                : "Select product..."}
              <ChevronsUpDown className="ml-2 h-4 w-4 opacity-50" />
            </Button>
            {errors[`tier:${tier.tempId}:linkedProduct`] ? (
              <p className="text-xs text-destructive">
                {errors[`tier:${tier.tempId}:linkedProduct`]}
              </p>
            ) : null}
            <SelectShipmentProductDialog
              open={productPickerOpen}
              onOpenChange={setProductPickerOpen}
              onSelect={(product) => {
                onLinkedProductSelect(tier.tempId, product);
                setProductPickerOpen(false);
              }}
            />
          </div>

          {tier.linkedProductId ? (
            <div className="grid gap-1.5">
              <Label className="text-xs">Source Location (transfer-in)</Label>
              {inventoryQuery.isLoading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading locations…
                </div>
              ) : (
                <ProductLocationSelector
                  inventoryEntries={availableEntries}
                  value={tier.source}
                  onChange={(v) => onUpdate(tier.tempId, { source: v })}
                  disabled={isPending}
                />
              )}
              {errors[`tier:${tier.tempId}:source`] ? (
                <p className="text-xs text-destructive">
                  {errors[`tier:${tier.tempId}:source`]}
                </p>
              ) : null}
            </div>
          ) : null}
        </>
      ) : (
        <>
          <div className="grid gap-1.5">
            <Label className="text-xs">Prize name</Label>
            <Input
              value={tier.productName}
              onChange={(e) =>
                onUpdate(tier.tempId, { productName: e.target.value })
              }
              placeholder="e.g. Holographic Charizard"
              disabled={isPending}
            />
            {errors[`tier:${tier.tempId}:productName`] ? (
              <p className="text-xs text-destructive">
                {errors[`tier:${tier.tempId}:productName`]}
              </p>
            ) : null}
          </div>

          <div className="grid gap-1.5">
            <Label className="text-xs">
              Image{" "}
              <span className="text-muted-foreground font-normal">
                (optional)
              </span>
            </Label>
            <ImageUpload
              displayUrl={
                tier.productImagePreviewUrl ||
                (tier.productImageUrl || null)
              }
              isUploading={false}
              error={null}
              hasNewFile={tier.productImageFile != null}
              onFileSelect={(file) => {
                if (!file) {
                  if (tier.productImagePreviewUrl) {
                    URL.revokeObjectURL(tier.productImagePreviewUrl);
                  }
                  onUpdate(tier.tempId, {
                    productImageFile: null,
                    productImagePreviewUrl: null,
                  });
                  return;
                }
                const validationError = validateFile(file);
                if (validationError) {
                  onUpdate(tier.tempId, {});
                  return;
                }
                if (tier.productImagePreviewUrl) {
                  URL.revokeObjectURL(tier.productImagePreviewUrl);
                }
                const previewUrl = URL.createObjectURL(file);
                onUpdate(tier.tempId, {
                  productImageFile: file,
                  productImagePreviewUrl: previewUrl,
                });
              }}
              onClear={() => {
                if (tier.productImagePreviewUrl) {
                  URL.revokeObjectURL(tier.productImagePreviewUrl);
                }
                onUpdate(tier.tempId, {
                  productImageFile: null,
                  productImagePreviewUrl: null,
                  productImageUrl: "",
                });
              }}
              disabled={isPending}
            />
          </div>
        </>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label className="text-xs">Slips</Label>
          <Input
            type="number"
            min={0}
            value={tier.count}
            onChange={(e) => {
              const raw = e.target.value;
              if (raw === "") {
                onUpdate(tier.tempId, { count: "" });
                return;
              }
              const v = parseInt(raw, 10);
              if (!Number.isNaN(v) && v >= 0) {
                onUpdate(tier.tempId, { count: v });
              }
            }}
            disabled={isPending}
          />
          {errors[`tier:${tier.tempId}:count`] ? (
            <p className="text-xs text-destructive">
              {errors[`tier:${tier.tempId}:count`]}
            </p>
          ) : null}
        </div>
        <div className="grid gap-1.5">
          <Label className="text-xs">
            Held back{" "}
            <span className="text-muted-foreground font-normal">
              (not in slips)
            </span>
          </Label>
          <Input
            type="number"
            min={0}
            value={tier.heldBack}
            onChange={(e) => {
              const raw = e.target.value;
              if (raw === "") {
                onUpdate(tier.tempId, { heldBack: "" });
                return;
              }
              const v = parseInt(raw, 10);
              if (!Number.isNaN(v) && v >= 0) {
                onUpdate(tier.tempId, { heldBack: v });
              }
            }}
            disabled={isPending}
            placeholder="0"
          />
          {errors[`tier:${tier.tempId}:heldBack`] ? (
            <p className="text-xs text-destructive">
              {errors[`tier:${tier.tempId}:heldBack`]}
            </p>
          ) : (
            <p className="text-[11px] text-muted-foreground">
              {tier.linkedProductId
                ? "Extra units transferred to box location, not added to slips."
                : "Inventory units in the box that are not in slips."}
            </p>
          )}
        </div>
      </div>

      <div className="grid gap-1.5">
        <Label className="text-xs">Price</Label>
        <Input
          type="number"
          step="0.01"
          min={0}
          value={tier.price}
          onChange={(e) => onUpdate(tier.tempId, { price: e.target.value })}
          placeholder="0.00"
          disabled={isPending}
        />
        {errors[`tier:${tier.tempId}:price`] ? (
          <p className="text-xs text-destructive">
            {errors[`tier:${tier.tempId}:price`]}
          </p>
        ) : null}
      </div>
    </div>
  );
}
