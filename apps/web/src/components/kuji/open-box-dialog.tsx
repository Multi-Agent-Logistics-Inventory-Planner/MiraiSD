"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, Trash2, Copy } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LocationSelector } from "@/components/stock/location-selector";
import { ProductLocationSelector } from "@/components/stock/product-location-selector";
import { ImageUpload } from "@/components/ui/image-upload";
import {
  isUploadError,
  uploadProductImage,
  validateFile,
} from "@/lib/supabase/storage";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useOpenKujiBoxMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useLastClosedKujiTiers } from "@/hooks/queries/use-kuji-box";
import { useProducts } from "@/hooks/queries/use-products";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import type { NewKujiBoxTier, Product } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

interface OpenBoxDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly productId: string;
  readonly productName: string;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

type TierMode = "existing" | "create";

interface DraftTier {
  tempId: string;
  label: string;
  letter: string;
  mode: TierMode;
  /** Used when mode === "existing". */
  linkedProductId: string;
  /** Used when mode === "existing". */
  source: LocationSelection;
  /** Used when mode === "create". */
  productName: string;
  /** Used when mode === "create". Pending File before upload. */
  productImageFile: File | null;
  /** Local object URL for preview before upload. */
  productImagePreviewUrl: string | null;
  /** Already-uploaded URL (e.g. cloned from a previous box). */
  productImageUrl: string;
  count: number | "";
  heldBack: number | "";
  price: string;
}

function blankTier(): DraftTier {
  return {
    tempId: crypto.randomUUID(),
    label: "",
    letter: "",
    mode: "create",
    linkedProductId: "",
    source: EMPTY_LOCATION,
    productName: "",
    productImageFile: null,
    productImagePreviewUrl: null,
    productImageUrl: "",
    count: "",
    heldBack: "",
    price: "",
  };
}

export function OpenBoxDialog({
  open,
  onOpenChange,
  productId,
  productName,
}: OpenBoxDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const openBox = useOpenKujiBoxMutation();
  const productsQuery = useProducts({ excludeCustomKuji: true });
  const lastTiersQuery = useLastClosedKujiTiers(open ? productId : null);

  const [boxLocation, setBoxLocation] =
    useState<LocationSelection>(EMPTY_LOCATION);
  const [tiers, setTiers] = useState<DraftTier[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // NOTE: machine-display picker is intentionally omitted — there is no
  // `useActiveMachineDisplaysForProduct` hook that maps a product to its
  // active machine displays in the codebase. Tracked as a follow-up.

  useEffect(() => {
    if (open) {
      setBoxLocation(EMPTY_LOCATION);
      setTiers([blankTier()]);
      setErrors({});
    }
  }, [open]);

  function handleCloneFromLast() {
    const data = lastTiersQuery.data;
    if (!data || data.length === 0) {
      toast({
        title: "No previous box",
        description: "There is no closed box to clone from.",
      });
      return;
    }
    setTiers(
      data.map((t) => ({
        tempId: crypto.randomUUID(),
        label: t.label,
        letter: t.letter ?? "",
        // Backend strips linkedProductId from cloned auto-created tiers, so falling back
        // to "create" mode when there's no linked product matches that intent.
        mode: t.linkedProductId ? "existing" : "create",
        linkedProductId: t.linkedProductId ?? "",
        source: EMPTY_LOCATION,
        productName: "",
        productImageFile: null,
        productImagePreviewUrl: null,
        productImageUrl: "",
        count: t.count,
        heldBack: "",
        price: t.price != null ? String(t.price) : "",
      })),
    );
    toast({ title: `Cloned ${data.length} tier(s)`, variant: "success" });
  }

  function handleAddTier() {
    setTiers((prev) => [...prev, blankTier()]);
  }

  function handleRemoveTier(tempId: string) {
    setTiers((prev) => prev.filter((t) => t.tempId !== tempId));
  }

  function updateTier(tempId: string, updates: Partial<DraftTier>) {
    setTiers((prev) =>
      prev.map((t) => (t.tempId === tempId ? { ...t, ...updates } : t)),
    );
    // Clear errors for this tier
    setErrors((prev) => {
      const next: Record<string, string> = {};
      for (const k of Object.keys(prev)) {
        if (!k.startsWith(`tier:${tempId}:`)) {
          next[k] = prev[k];
        }
      }
      return next;
    });
  }

  function handleLinkedProductChange(tempId: string, nextProductId: string) {
    const product = (productsQuery.data ?? []).find(
      (p) => p.id === nextProductId,
    );
    const msrp = product?.msrp;
    setTiers((prev) =>
      prev.map((t) => {
        if (t.tempId !== tempId) return t;
        return {
          ...t,
          linkedProductId: nextProductId,
          source: EMPTY_LOCATION,
          price: msrp != null ? String(msrp) : t.price,
        };
      }),
    );
    setErrors((prev) => {
      const next: Record<string, string> = {};
      for (const k of Object.keys(prev)) {
        if (!k.startsWith(`tier:${tempId}:`)) {
          next[k] = prev[k];
        }
      }
      return next;
    });
  }

  function validate(): boolean {
    const nextErrors: Record<string, string> = {};
    if (!boxLocation.locationType || !boxLocation.locationId) {
      nextErrors.boxLocation = "Box location is required";
    }
    if (tiers.length === 0) {
      nextErrors.tiers = "At least one tier is required";
    }
    tiers.forEach((tier) => {
      if (!tier.label.trim()) {
        nextErrors[`tier:${tier.tempId}:label`] = "Label is required";
      }
      if (tier.count === "" || (typeof tier.count === "number" && tier.count < 0)) {
        nextErrors[`tier:${tier.tempId}:count`] = "Slips must be ≥ 0";
      }
      if (typeof tier.heldBack === "number" && tier.heldBack < 0) {
        nextErrors[`tier:${tier.tempId}:heldBack`] = "Held back must be ≥ 0";
      }
      if (tier.mode === "existing") {
        if (!tier.linkedProductId) {
          nextErrors[`tier:${tier.tempId}:linkedProduct`] = "Pick a product";
        } else if (!tier.source.locationType || !tier.source.locationId) {
          nextErrors[`tier:${tier.tempId}:source`] =
            "Source location is required when linking a product";
        }
      } else {
        if (!tier.productName.trim()) {
          nextErrors[`tier:${tier.tempId}:productName`] = "Prize name is required";
        }
      }
      if (tier.price && Number.isNaN(parseFloat(tier.price))) {
        nextErrors[`tier:${tier.tempId}:price`] = "Price must be a number";
      }
    });
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }
    if (!validate()) return;
    if (!boxLocation.locationId) return;

    // Upload pending images for create-mode tiers before sending the payload.
    const uploadedUrlByTempId = new Map<string, string>();
    try {
      for (const t of tiers) {
        if (t.mode === "create" && t.productImageFile) {
          const result = await uploadProductImage(t.productImageFile);
          if (isUploadError(result)) {
            toast({
              title: "Image upload failed",
              description: result.message,
            });
            return;
          }
          uploadedUrlByTempId.set(t.tempId, result.url);
        }
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Image upload failed";
      toast({ title: "Image upload failed", description: message });
      return;
    }

    const payloadTiers: NewKujiBoxTier[] = tiers.map((t) => {
      const isCreate = t.mode === "create";
      const uploadedImageUrl =
        uploadedUrlByTempId.get(t.tempId) ?? (t.productImageUrl || null);
      const parsedPrice = t.price === "" ? null : parseFloat(t.price);
      return {
        label: t.label.trim(),
        letter: t.letter.trim() || null,
        linkedProductId: isCreate ? null : t.linkedProductId || null,
        sourceLocationId:
          !isCreate && t.linkedProductId ? t.source.locationId : null,
        count: t.count === "" ? 0 : t.count,
        heldBackQuantity: typeof t.heldBack === "number" ? t.heldBack : 0,
        price: parsedPrice,
        autoCreate: isCreate,
        productName: isCreate ? t.productName.trim() : null,
        productImageUrl: isCreate ? uploadedImageUrl : null,
        productMsrp: isCreate ? parsedPrice : null,
      };
    });

    try {
      await openBox.mutateAsync({
        productId,
        locationId: boxLocation.locationId,
        machineDisplayId: null,
        label: null,
        notes: null,
        tiers: payloadTiers,
        actorId,
      });
      toast({ title: "Box opened", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to open box";
      toast({ title: "Open box failed", description: message });
    }
  }

  const isPending = openBox.isPending;
  const hasLastTiers = (lastTiersQuery.data?.length ?? 0) > 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-2xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="p-6 pb-2">
          <DialogTitle>Open Kuji Box for {productName}</DialogTitle>
          <DialogDescription>
            Configure the location and tier list for the new kuji box.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 pb-4 space-y-4">
          <div className="grid gap-2">
            <Label>Box Location</Label>
            <LocationSelector
              label=""
              value={boxLocation}
              onChange={setBoxLocation}
              disabled={isPending}
              excludeDisplayOnly
            />
            {errors.boxLocation ? (
              <p className="text-xs text-destructive">{errors.boxLocation}</p>
            ) : null}
          </div>

          <div className="flex items-center justify-between gap-2 pt-2">
            <span className="text-sm font-medium">Tiers</span>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCloneFromLast}
                disabled={
                  isPending ||
                  lastTiersQuery.isLoading ||
                  !hasLastTiers
                }
                title={
                  hasLastTiers
                    ? "Clone tiers from last closed box"
                    : "No previous box to clone"
                }
              >
                <Copy className="h-4 w-4 mr-1" />
                Clone from Last
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAddTier}
                disabled={isPending}
              >
                <Plus className="h-4 w-4 mr-1" />
                Add Tier
              </Button>
            </div>
          </div>

          {errors.tiers ? (
            <p className="text-xs text-destructive">{errors.tiers}</p>
          ) : null}

          <div className="space-y-3">
            {tiers.map((tier, idx) => (
              <TierRow
                key={tier.tempId}
                tier={tier}
                index={idx}
                products={productsQuery.data ?? []}
                errors={errors}
                isPending={isPending}
                canRemove={tiers.length > 1}
                onUpdate={updateTier}
                onLinkedProductChange={handleLinkedProductChange}
                onRemove={handleRemoveTier}
              />
            ))}
          </div>
        </div>

        <DialogFooter className="px-6 py-4 border-t">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={isPending}>
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Open Box
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface TierRowProps {
  readonly tier: DraftTier;
  readonly index: number;
  readonly products: Product[];
  readonly errors: Record<string, string>;
  readonly isPending: boolean;
  readonly canRemove: boolean;
  readonly onUpdate: (tempId: string, updates: Partial<DraftTier>) => void;
  readonly onLinkedProductChange: (tempId: string, productId: string) => void;
  readonly onRemove: (tempId: string) => void;
}

function TierRow({
  tier,
  index,
  products,
  errors,
  isPending,
  canRemove,
  onUpdate,
  onLinkedProductChange,
  onRemove,
}: TierRowProps) {
  const inventoryQuery = useProductInventoryEntries(
    tier.linkedProductId || null,
  );
  const availableEntries = (inventoryQuery.data?.entries ?? []).filter(
    (e) => e.locationId !== null && e.quantity > 0,
  );

  return (
    <div className="border rounded-md p-3 space-y-3 bg-muted/30">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase text-muted-foreground">
          Tier {index + 1}
        </span>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-7 w-7"
          onClick={() => onRemove(tier.tempId)}
          disabled={isPending || !canRemove}
          aria-label={`Remove tier ${index + 1}`}
        >
          <Trash2 className="h-4 w-4 text-destructive" />
        </Button>
      </div>

      <div className="grid gap-1">
        <Label className="text-xs">Label</Label>
        <Input
          value={tier.label}
          onChange={(e) => onUpdate(tier.tempId, { label: e.target.value })}
          disabled={isPending}
        />
        {errors[`tier:${tier.tempId}:label`] ? (
          <p className="text-xs text-destructive">
            {errors[`tier:${tier.tempId}:label`]}
          </p>
        ) : null}
      </div>

      <div className="grid gap-1">
        <Label className="text-xs">Prize source</Label>
        <div className="flex gap-1 rounded-md border p-1">
          <button
            type="button"
            className={`flex-1 rounded px-3 py-1 text-xs ${
              tier.mode === "create"
                ? "bg-background font-medium"
                : "text-muted-foreground hover:bg-muted"
            }`}
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
            className={`flex-1 rounded px-3 py-1 text-xs ${
              tier.mode === "existing"
                ? "bg-background font-medium"
                : "text-muted-foreground hover:bg-muted"
            }`}
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
          <div className="grid gap-1">
            <Label className="text-xs">Linked Product</Label>
            <select
              className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              value={tier.linkedProductId}
              onChange={(e) => onLinkedProductChange(tier.tempId, e.target.value)}
              disabled={isPending}
            >
              <option value="">— Pick a product —</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
            {errors[`tier:${tier.tempId}:linkedProduct`] ? (
              <p className="text-xs text-destructive">
                {errors[`tier:${tier.tempId}:linkedProduct`]}
              </p>
            ) : null}
          </div>

          {tier.linkedProductId ? (
            <div className="grid gap-1">
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
          <div className="grid gap-1">
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

          <div className="grid gap-1">
            <Label className="text-xs">Image (optional)</Label>
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
                  // Surface as a tier-level error
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
        <div className="grid gap-1">
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
        <div className="grid gap-1">
          <Label className="text-xs">Held back (not in slips)</Label>
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
            <p className="text-xs text-muted-foreground">
              {tier.linkedProductId
                ? "Extra units transferred to box location, not added to slips."
                : "Inventory units in the box that are not in slips."}
            </p>
          )}
        </div>
      </div>

      <div className="grid gap-1">
        <Label className="text-xs">Price</Label>
        <Input
          type="number"
          step="0.01"
          min={0}
          value={tier.price}
          onChange={(e) => onUpdate(tier.tempId, { price: e.target.value })}
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
