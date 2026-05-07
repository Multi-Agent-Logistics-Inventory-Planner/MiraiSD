"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
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
import { ImageUpload } from "@/components/ui/image-upload";
import { LocationSelector } from "@/components/stock/location-selector";
import {
  isUploadError,
  uploadProductImage,
  validateFile,
} from "@/lib/supabase/storage";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { usePatchKujiTierMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useUpdateProductMutation } from "@/hooks/mutations/use-product-mutations";
import { useProducts } from "@/hooks/queries/use-products";
import type {
  KujiBox,
  KujiBoxTier,
  PatchKujiTierRequest,
  ProductRequest,
} from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

interface TierEditDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
  readonly tier: KujiBoxTier;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

export function TierEditDialog({
  open,
  onOpenChange,
  box,
  tier,
}: TierEditDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const patchTier = usePatchKujiTierMutation();
  const updateProduct = useUpdateProductMutation();
  const productsQuery = useProducts({ excludeCustomKuji: true });

  const isAutoCreated = Boolean(tier.autoCreatedProduct);

  const [label, setLabel] = useState(tier.label);
  const [linkedProductId, setLinkedProductId] = useState(
    tier.linkedProductId ?? "",
  );
  const [clearLinkedProduct, setClearLinkedProduct] = useState(false);
  const [price, setPrice] = useState<string>(
    tier.price != null ? String(tier.price) : "",
  );
  const [clearPrice, setClearPrice] = useState(false);
  const [count, setCount] = useState<number | "">(tier.count);
  const [linkedDest, setLinkedDest] =
    useState<LocationSelection>(EMPTY_LOCATION);
  // Auto-created product fields
  const [productName, setProductName] = useState(tier.linkedProductName ?? "");
  const [productImageFile, setProductImageFile] = useState<File | null>(null);
  const [productImagePreviewUrl, setProductImagePreviewUrl] = useState<
    string | null
  >(null);
  const [productImageUrl, setProductImageUrl] = useState<string>(
    tier.linkedProductImageUrl ?? "",
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (open) {
      setLabel(tier.label);
      setLinkedProductId(tier.linkedProductId ?? "");
      setClearLinkedProduct(false);
      setPrice(tier.price != null ? String(tier.price) : "");
      setClearPrice(false);
      setCount(tier.count);
      setLinkedDest(EMPTY_LOCATION);
      setProductName(tier.linkedProductName ?? "");
      setProductImageFile(null);
      if (productImagePreviewUrl) {
        URL.revokeObjectURL(productImagePreviewUrl);
      }
      setProductImagePreviewUrl(null);
      setProductImageUrl(tier.linkedProductImageUrl ?? "");
      setErrors({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, tier]);

  const previousLinkedProductId = tier.linkedProductId ?? null;
  const linkedProductChanged =
    clearLinkedProduct ||
    (linkedProductId || null) !== previousLinkedProductId;

  // Show transfer-out picker when previous linked product had inventory at the box location.
  const needsDestinationForOldProduct =
    !!previousLinkedProductId &&
    linkedProductChanged &&
    (tier.linkedInventoryAtBoxLocation ?? 0) > 0;

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    const nextErrors: Record<string, string> = {};
    if (!label.trim()) nextErrors.label = "Label is required";
    if (count === "" || count < 0) nextErrors.count = "Count must be ≥ 0";
    if (price && Number.isNaN(parseFloat(price))) {
      nextErrors.price = "Price must be a number";
    }
    if (isAutoCreated && !productName.trim()) {
      nextErrors.productName = "Prize name is required";
    }

    if (needsDestinationForOldProduct) {
      if (!linkedDest.locationType || !linkedDest.locationId) {
        nextErrors.linkedDest = "Destination is required for previous linked inventory";
      }
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    // Auto-created prize: persist product changes (name/image) before tier patch.
    if (isAutoCreated && tier.linkedProductId) {
      const nameChanged = productName.trim() !== (tier.linkedProductName ?? "");
      const imageChanged = productImageFile != null
        || productImageUrl !== (tier.linkedProductImageUrl ?? "");

      if (nameChanged || imageChanged) {
        let nextImageUrl: string | undefined = productImageUrl || undefined;
        if (productImageFile) {
          const result = await uploadProductImage(productImageFile);
          if (isUploadError(result)) {
            toast({
              title: "Image upload failed",
              description: result.message,
            });
            return;
          }
          nextImageUrl = result.url;
        }

        const productPayload: ProductRequest = {
          name: productName.trim(),
          imageUrl: nextImageUrl,
        };

        try {
          await updateProduct.mutateAsync({
            id: tier.linkedProductId,
            payload: productPayload,
          });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : "Failed to update prize";
          toast({ title: "Update failed", description: message });
          return;
        }
      }
    }

    const payload: PatchKujiTierRequest = { actorId };

    if (label.trim() !== tier.label) {
      payload.label = label.trim();
    }
    // Auto-created tiers don't expose linked-product changes — the picker is hidden.
    if (!isAutoCreated) {
      if (clearLinkedProduct) {
        payload.clearLinkedProduct = true;
      } else if ((linkedProductId || null) !== previousLinkedProductId) {
        payload.linkedProductId = linkedProductId || null;
      }
    }
    if (clearPrice) {
      payload.clearPrice = true;
    } else if (price !== (tier.price != null ? String(tier.price) : "")) {
      payload.price = price === "" ? null : parseFloat(price);
    }
    if (count !== "" && count !== tier.count) {
      payload.count = count;
    }
    if (needsDestinationForOldProduct && linkedDest.locationId) {
      payload.linkedProductDestinationLocationId = linkedDest.locationId;
    }

    try {
      await patchTier.mutateAsync({
        boxId: box.id,
        tierId: tier.id,
        productId: box.productId,
        payload,
      });
      toast({ title: "Tier updated", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to patch tier";
      toast({ title: "Update failed", description: message });
    }
  }

  const isPending = patchTier.isPending || updateProduct.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="p-6 pb-2">
          <DialogTitle>Edit Tier</DialogTitle>
          <DialogDescription>
            Modify properties of this tier. Linked-product changes may require
            inventory transfers.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 pb-4 space-y-4">
          <div className="grid gap-2">
            <Label htmlFor="tier-edit-label">Label</Label>
            <Input
              id="tier-edit-label"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              disabled={isPending}
            />
            {errors.label ? (
              <p className="text-xs text-destructive">{errors.label}</p>
            ) : null}
          </div>

          {isAutoCreated ? (
            <>
              <div className="grid gap-2">
                <Label htmlFor="tier-edit-prize-name">Prize name</Label>
                <Input
                  id="tier-edit-prize-name"
                  value={productName}
                  onChange={(e) => setProductName(e.target.value)}
                  disabled={isPending}
                />
                {errors.productName ? (
                  <p className="text-xs text-destructive">{errors.productName}</p>
                ) : null}
              </div>
              <div className="grid gap-2">
                <Label>Image</Label>
                <ImageUpload
                  displayUrl={productImagePreviewUrl || (productImageUrl || null)}
                  isUploading={false}
                  error={null}
                  hasNewFile={productImageFile != null}
                  onFileSelect={(file) => {
                    if (!file) {
                      if (productImagePreviewUrl) {
                        URL.revokeObjectURL(productImagePreviewUrl);
                      }
                      setProductImageFile(null);
                      setProductImagePreviewUrl(null);
                      return;
                    }
                    const validationError = validateFile(file);
                    if (validationError) {
                      toast({
                        title: "Invalid image",
                        description: validationError.message,
                      });
                      return;
                    }
                    if (productImagePreviewUrl) {
                      URL.revokeObjectURL(productImagePreviewUrl);
                    }
                    const previewUrl = URL.createObjectURL(file);
                    setProductImageFile(file);
                    setProductImagePreviewUrl(previewUrl);
                  }}
                  onClear={() => {
                    if (productImagePreviewUrl) {
                      URL.revokeObjectURL(productImagePreviewUrl);
                    }
                    setProductImageFile(null);
                    setProductImagePreviewUrl(null);
                    setProductImageUrl("");
                  }}
                  disabled={isPending}
                />
              </div>
            </>
          ) : (
            <div className="grid gap-2">
              <Label htmlFor="tier-edit-linked">Linked Product</Label>
              <div className="flex gap-2">
                <select
                  id="tier-edit-linked"
                  className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={linkedProductId}
                  onChange={(e) => {
                    setLinkedProductId(e.target.value);
                    setClearLinkedProduct(false);
                  }}
                  disabled={isPending || clearLinkedProduct}
                >
                  <option value="">— None —</option>
                  {(productsQuery.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setLinkedProductId("");
                    setClearLinkedProduct(true);
                  }}
                  disabled={isPending}
                >
                  Clear
                </Button>
              </div>
            </div>
          )}

          {needsDestinationForOldProduct ? (
            <div className="grid gap-2 border rounded-md p-3 bg-muted/40">
              <Label className="text-xs">
                Old linked product has{" "}
                {(tier.linkedInventoryAtBoxLocation ?? 0).toLocaleString()} units
                at the box&apos;s location — pick a destination
              </Label>
              <LocationSelector
                label=""
                value={linkedDest}
                onChange={(v) => {
                  setLinkedDest(v);
                  setErrors((prev) => ({ ...prev, linkedDest: "" }));
                }}
                disabled={isPending}
                excludeDisplayOnly
              />
              {errors.linkedDest ? (
                <p className="text-xs text-destructive">{errors.linkedDest}</p>
              ) : null}
            </div>
          ) : null}

          <div className="grid gap-2">
            <Label htmlFor="tier-edit-price">Price</Label>
            <div className="flex gap-2">
              <Input
                id="tier-edit-price"
                type="number"
                step="0.01"
                min={0}
                value={price}
                onChange={(e) => {
                  setPrice(e.target.value);
                  setClearPrice(false);
                }}
                disabled={isPending || clearPrice}
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  setPrice("");
                  setClearPrice(true);
                }}
                disabled={isPending}
              >
                Clear
              </Button>
            </div>
            {errors.price ? (
              <p className="text-xs text-destructive">{errors.price}</p>
            ) : null}
          </div>

          <div className="grid gap-2">
            <Label htmlFor="tier-edit-count">Count</Label>
            <Input
              id="tier-edit-count"
              type="number"
              min={0}
              value={count}
              onChange={(e) => {
                const raw = e.target.value;
                if (raw === "") {
                  setCount("");
                  return;
                }
                const v = parseInt(raw, 10);
                if (!Number.isNaN(v) && v >= 0) {
                  setCount(v);
                }
              }}
              disabled={isPending}
            />
            {errors.count ? (
              <p className="text-xs text-destructive">{errors.count}</p>
            ) : null}
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
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
