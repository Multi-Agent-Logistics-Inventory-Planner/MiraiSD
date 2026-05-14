"use client";

import { useEffect, useState } from "react";
import { ChevronsUpDown, Loader2 } from "lucide-react";
import { SelectShipmentProductDialog } from "@/components/shipments/select-shipment-product-dialog";
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
import { ProductLocationSelector } from "@/components/stock/product-location-selector";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import {
  isUploadError,
  uploadProductImage,
  validateFile,
} from "@/lib/supabase/storage";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { usePatchKujiTierMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useUpdateProductMutation } from "@/hooks/mutations/use-product-mutations";
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
  const isAutoCreated = Boolean(tier.autoCreatedProduct);

  const [label, setLabel] = useState(tier.label);
  const [linkedProductId, setLinkedProductId] = useState(
    tier.linkedProductId ?? "",
  );
  const [linkedProductDisplayName, setLinkedProductDisplayName] = useState(
    tier.linkedProductName ?? "",
  );
  const [linkedProductDisplaySku, setLinkedProductDisplaySku] = useState<
    string | null
  >(null);
  const [productPickerOpen, setProductPickerOpen] = useState(false);
  const [clearLinkedProduct, setClearLinkedProduct] = useState(false);
  const [price, setPrice] = useState<string>(
    tier.price != null ? String(tier.price) : "",
  );
  const [clearPrice, setClearPrice] = useState(false);
  const [activeCount, setActiveCountState] = useState<number | "">(tier.activeCount);
  const [inactiveCount, setInactiveCountState] = useState<number | "">(
    tier.inactiveCount,
  );
  const [linkedDest, setLinkedDest] =
    useState<LocationSelection>(EMPTY_LOCATION);
  // Optional: bring in counts of the newly-linked product in the same save.
  const [newProductSource, setNewProductSource] =
    useState<LocationSelection>(EMPTY_LOCATION);
  const [newProductActive, setNewProductActive] = useState<number | "">("");
  const [newProductInactive, setNewProductInactive] = useState<number | "">("");
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
      setLinkedProductDisplayName(tier.linkedProductName ?? "");
      setLinkedProductDisplaySku(null);
      setProductPickerOpen(false);
      setClearLinkedProduct(false);
      setPrice(tier.price != null ? String(tier.price) : "");
      setClearPrice(false);
      setActiveCountState(tier.activeCount);
      setInactiveCountState(tier.inactiveCount);
      setLinkedDest(EMPTY_LOCATION);
      setNewProductSource(EMPTY_LOCATION);
      setNewProductActive("");
      setNewProductInactive("");
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

  // When the user switches (or clears) the linked product, the backend returns
  // active+inactive slips of the OLD product to regular inventory — which needs a
  // destination location. Only required when there are leftover slips to return.
  const leftoverOnOldProduct = tier.activeCount + tier.inactiveCount;
  const needsDestinationForOldProduct =
    !!previousLinkedProductId
    && linkedProductChanged
    && leftoverOnOldProduct > 0;
  // Show the optional "bring in new product" panel only when switching to a real
  // new linked product (not clearing). Hidden for auto-created tiers — those never
  // expose linked-product changes via this dialog.
  const canBringInNewProduct =
    !isAutoCreated
    && linkedProductChanged
    && !clearLinkedProduct
    && !!linkedProductId;
  const newProductActiveQty = typeof newProductActive === "number" ? newProductActive : 0;
  const newProductInactiveQty = typeof newProductInactive === "number" ? newProductInactive : 0;
  const newProductTotalIn = newProductActiveQty + newProductInactiveQty;
  const needsNewProductSource = canBringInNewProduct && newProductTotalIn > 0;

  // Load locations the newly-selected linked product actually lives at so the
  // source picker only offers real candidates.
  const newProductInventoryQuery = useProductInventoryEntries(
    canBringInNewProduct ? linkedProductId : null,
  );
  const newProductAvailableEntries =
    (newProductInventoryQuery.data?.entries ?? []).filter(
      (e) => e.locationId !== null,
    );

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    const nextErrors: Record<string, string> = {};
    if (!label.trim()) nextErrors.label = "Label is required";
    if (activeCount === "" || activeCount < 0)
      nextErrors.activeCount = "Active count must be ≥ 0";
    if (inactiveCount === "" || inactiveCount < 0)
      nextErrors.inactiveCount = "Inactive count must be ≥ 0";
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

    if (needsNewProductSource) {
      if (!newProductSource.locationType || !newProductSource.locationId) {
        nextErrors.newProductSource = "Source location is required";
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
    if (activeCount !== "" && activeCount !== tier.activeCount) {
      payload.activeCount = activeCount;
    }
    if (inactiveCount !== "" && inactiveCount !== tier.inactiveCount) {
      payload.inactiveCount = inactiveCount;
    }
    if (needsDestinationForOldProduct && linkedDest.locationId) {
      payload.linkedProductDestinationLocationId = linkedDest.locationId;
    }
    if (canBringInNewProduct && newProductTotalIn > 0) {
      payload.newProductSourceLocationId = newProductSource.locationId ?? null;
      payload.newProductActiveCount = newProductActiveQty;
      payload.newProductInactiveCount = newProductInactiveQty;
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
                <Button
                  id="tier-edit-linked"
                  type="button"
                  variant="outline"
                  className="justify-between flex-1"
                  disabled={isPending || clearLinkedProduct}
                  onClick={() => setProductPickerOpen(true)}
                >
                  {linkedProductId
                    ? linkedProductDisplaySku
                      ? `${linkedProductDisplayName} (${linkedProductDisplaySku})`
                      : linkedProductDisplayName || "—"
                    : "Select product..."}
                  <ChevronsUpDown className="ml-2 h-4 w-4 opacity-50" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setLinkedProductId("");
                    setLinkedProductDisplayName("");
                    setLinkedProductDisplaySku(null);
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
                Tier still has {leftoverOnOldProduct.toLocaleString()} slip
                {leftoverOnOldProduct === 1 ? "" : "s"} of{" "}
                {tier.linkedProductName ?? "the old product"}. Pick a destination to
                return them to inventory.
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

          {canBringInNewProduct ? (
            <div className="grid gap-2 border rounded-md p-3 bg-muted/40">
              <Label className="text-xs">
                Bring in the new product (optional). Leave at 0 to skip.
              </Label>
              <div className="grid grid-cols-2 gap-3">
                <div className="grid gap-2">
                  <Label
                    htmlFor="tier-edit-new-active"
                    className="text-[11px] text-muted-foreground"
                  >
                    New active count
                  </Label>
                  <Input
                    id="tier-edit-new-active"
                    type="number"
                    min={0}
                    value={newProductActive}
                    onChange={(e) => {
                      const raw = e.target.value;
                      if (raw === "") {
                        setNewProductActive("");
                        return;
                      }
                      const v = parseInt(raw, 10);
                      if (!Number.isNaN(v) && v >= 0) {
                        setNewProductActive(v);
                      }
                    }}
                    disabled={isPending}
                  />
                </div>
                <div className="grid gap-2">
                  <Label
                    htmlFor="tier-edit-new-inactive"
                    className="text-[11px] text-muted-foreground"
                  >
                    New inactive count
                  </Label>
                  <Input
                    id="tier-edit-new-inactive"
                    type="number"
                    min={0}
                    value={newProductInactive}
                    onChange={(e) => {
                      const raw = e.target.value;
                      if (raw === "") {
                        setNewProductInactive("");
                        return;
                      }
                      const v = parseInt(raw, 10);
                      if (!Number.isNaN(v) && v >= 0) {
                        setNewProductInactive(v);
                      }
                    }}
                    disabled={isPending}
                  />
                </div>
              </div>
              {needsNewProductSource ? (
                <>
                  <Label className="text-[11px] text-muted-foreground">
                    Source for {newProductTotalIn.toLocaleString()} unit
                    {newProductTotalIn === 1 ? "" : "s"}
                  </Label>
                  {newProductInventoryQuery.isLoading ? (
                    <p className="text-xs text-muted-foreground">
                      Loading locations…
                    </p>
                  ) : newProductAvailableEntries.length === 0 ? (
                    <p className="text-xs text-destructive">
                      No locations have this product in stock. Bring it in via the
                      regular inventory flow first.
                    </p>
                  ) : (
                    <ProductLocationSelector
                      inventoryEntries={newProductAvailableEntries}
                      value={newProductSource}
                      onChange={(v) => {
                        setNewProductSource(v);
                        setErrors((prev) => ({ ...prev, newProductSource: "" }));
                      }}
                      disabled={isPending}
                    />
                  )}
                  {errors.newProductSource ? (
                    <p className="text-xs text-destructive">
                      {errors.newProductSource}
                    </p>
                  ) : null}
                </>
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

          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="tier-edit-active-count">Active count</Label>
              <Input
                id="tier-edit-active-count"
                type="number"
                min={0}
                value={activeCount}
                onChange={(e) => {
                  const raw = e.target.value;
                  if (raw === "") {
                    setActiveCountState("");
                    return;
                  }
                  const v = parseInt(raw, 10);
                  if (!Number.isNaN(v) && v >= 0) {
                    setActiveCountState(v);
                  }
                }}
                disabled={isPending}
              />
              {errors.activeCount ? (
                <p className="text-xs text-destructive">{errors.activeCount}</p>
              ) : null}
            </div>
            <div className="grid gap-2">
              <Label htmlFor="tier-edit-inactive-count">Inactive count</Label>
              <Input
                id="tier-edit-inactive-count"
                type="number"
                min={0}
                value={inactiveCount}
                onChange={(e) => {
                  const raw = e.target.value;
                  if (raw === "") {
                    setInactiveCountState("");
                    return;
                  }
                  const v = parseInt(raw, 10);
                  if (!Number.isNaN(v) && v >= 0) {
                    setInactiveCountState(v);
                  }
                }}
                disabled={isPending}
              />
              {errors.inactiveCount ? (
                <p className="text-xs text-destructive">{errors.inactiveCount}</p>
              ) : null}
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            Manual edit — audited as adjustment.
          </p>
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
      <SelectShipmentProductDialog
        open={productPickerOpen}
        onOpenChange={setProductPickerOpen}
        onSelect={(product) => {
          setLinkedProductId(product.id);
          setLinkedProductDisplayName(product.name);
          setLinkedProductDisplaySku(product.sku ?? null);
          setClearLinkedProduct(false);
          setProductPickerOpen(false);
        }}
      />
    </Dialog>
  );
}
