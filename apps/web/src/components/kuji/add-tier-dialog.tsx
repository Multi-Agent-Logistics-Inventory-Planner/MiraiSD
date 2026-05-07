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
import { ProductLocationSelector } from "@/components/stock/product-location-selector";
import {
  isUploadError,
  uploadProductImage,
  validateFile,
} from "@/lib/supabase/storage";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useAddKujiTierMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useProducts } from "@/hooks/queries/use-products";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import type { AddKujiTierRequest, KujiBox } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

interface AddTierDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

type TierMode = "existing" | "create";

export function AddTierDialog({ open, onOpenChange, box }: AddTierDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const addTier = useAddKujiTierMutation();
  const productsQuery = useProducts({ excludeCustomKuji: true });

  const [mode, setMode] = useState<TierMode>("create");
  const [label, setLabel] = useState("");
  const [linkedProductId, setLinkedProductId] = useState("");
  const [source, setSource] = useState<LocationSelection>(EMPTY_LOCATION);
  const [productName, setProductName] = useState("");
  const [productImageFile, setProductImageFile] = useState<File | null>(null);
  const [productImagePreviewUrl, setProductImagePreviewUrl] = useState<
    string | null
  >(null);
  const [count, setCount] = useState<number | "">(1);
  const [heldBack, setHeldBack] = useState<number | "">("");
  const [price, setPrice] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const inventoryQuery = useProductInventoryEntries(linkedProductId || null);
  const availableEntries = (inventoryQuery.data?.entries ?? []).filter(
    (e) => e.locationId !== null && e.quantity > 0,
  );

  useEffect(() => {
    if (open) {
      setMode("create");
      setLabel("");
      setLinkedProductId("");
      setSource(EMPTY_LOCATION);
      setProductName("");
      setProductImageFile(null);
      if (productImagePreviewUrl) {
        URL.revokeObjectURL(productImagePreviewUrl);
      }
      setProductImagePreviewUrl(null);
      setCount(1);
      setHeldBack("");
      setPrice("");
      setErrors({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function handleLinkedProductChange(nextId: string) {
    const product = (productsQuery.data ?? []).find((p) => p.id === nextId);
    setLinkedProductId(nextId);
    setSource(EMPTY_LOCATION);
    if (product?.msrp != null) {
      setPrice(String(product.msrp));
    }
    setErrors({});
  }

  function validate(): boolean {
    const next: Record<string, string> = {};
    if (!label.trim()) next.label = "Label is required";
    if (count === "" || (typeof count === "number" && count < 0)) {
      next.count = "Slips must be ≥ 0";
    }
    if (typeof heldBack === "number" && heldBack < 0) {
      next.heldBack = "Held back must be ≥ 0";
    }
    if (mode === "existing") {
      if (!linkedProductId) next.linkedProduct = "Pick a product";
      else if (!source.locationType || !source.locationId) {
        next.source = "Source location is required";
      }
    } else if (!productName.trim()) {
      next.productName = "Prize name is required";
    }
    if (price && Number.isNaN(parseFloat(price))) {
      next.price = "Price must be a number";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }
    if (!validate()) return;

    let uploadedImageUrl: string | null = null;
    if (mode === "create" && productImageFile) {
      const result = await uploadProductImage(productImageFile);
      if (isUploadError(result)) {
        toast({ title: "Image upload failed", description: result.message });
        return;
      }
      uploadedImageUrl = result.url;
    }

    const isCreate = mode === "create";
    const parsedPrice = price === "" ? null : parseFloat(price);
    const payload: AddKujiTierRequest = {
      actorId,
      label: label.trim(),
      linkedProductId: isCreate ? null : linkedProductId || null,
      sourceLocationId: !isCreate && linkedProductId ? source.locationId : null,
      count: count === "" ? 0 : count,
      heldBackQuantity: typeof heldBack === "number" ? heldBack : 0,
      price: parsedPrice,
      autoCreate: isCreate,
      productName: isCreate ? productName.trim() : null,
      productImageUrl: isCreate ? uploadedImageUrl : null,
      productMsrp: isCreate ? parsedPrice : null,
    };

    try {
      await addTier.mutateAsync({
        boxId: box.id,
        productId: box.productId,
        payload,
      });
      toast({ title: "Tier added", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to add tier";
      toast({ title: "Add tier failed", description: message });
    }
  }

  const isPending = addTier.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="p-6 pb-2">
          <DialogTitle>Add Tier</DialogTitle>
          <DialogDescription>
            Add a new prize tier to this open box.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 pb-4 space-y-4">
          <div className="grid gap-2">
            <Label>Label</Label>
            <Input
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              disabled={isPending}
            />
            {errors.label ? (
              <p className="text-xs text-destructive">{errors.label}</p>
            ) : null}
          </div>

          <div className="grid gap-1">
            <Label className="text-xs">Prize source</Label>
            <div className="flex gap-1 rounded-md border p-1">
              <button
                type="button"
                className={`flex-1 rounded px-3 py-1 text-xs ${
                  mode === "create"
                    ? "bg-background font-medium"
                    : "text-muted-foreground hover:bg-muted"
                }`}
                onClick={() => {
                  setMode("create");
                  setLinkedProductId("");
                  setSource(EMPTY_LOCATION);
                }}
                disabled={isPending}
              >
                Create new
              </button>
              <button
                type="button"
                className={`flex-1 rounded px-3 py-1 text-xs ${
                  mode === "existing"
                    ? "bg-background font-medium"
                    : "text-muted-foreground hover:bg-muted"
                }`}
                onClick={() => {
                  setMode("existing");
                  setProductName("");
                  setProductImageFile(null);
                  if (productImagePreviewUrl) {
                    URL.revokeObjectURL(productImagePreviewUrl);
                  }
                  setProductImagePreviewUrl(null);
                }}
                disabled={isPending}
              >
                Existing product
              </button>
            </div>
          </div>

          {mode === "existing" ? (
            <>
              <div className="grid gap-2">
                <Label>Linked Product</Label>
                <select
                  className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={linkedProductId}
                  onChange={(e) => handleLinkedProductChange(e.target.value)}
                  disabled={isPending}
                >
                  <option value="">— Pick a product —</option>
                  {(productsQuery.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
                {errors.linkedProduct ? (
                  <p className="text-xs text-destructive">{errors.linkedProduct}</p>
                ) : null}
              </div>

              {linkedProductId ? (
                <div className="grid gap-2">
                  <Label>Source Location (transfer-in)</Label>
                  {inventoryQuery.isLoading ? (
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Loading locations…
                    </div>
                  ) : (
                    <ProductLocationSelector
                      inventoryEntries={availableEntries}
                      value={source}
                      onChange={(v) => setSource(v)}
                      disabled={isPending}
                    />
                  )}
                  {errors.source ? (
                    <p className="text-xs text-destructive">{errors.source}</p>
                  ) : null}
                </div>
              ) : null}
            </>
          ) : (
            <>
              <div className="grid gap-2">
                <Label>Prize name</Label>
                <Input
                  value={productName}
                  onChange={(e) => setProductName(e.target.value)}
                  disabled={isPending}
                  placeholder="e.g. Holographic Charizard"
                />
                {errors.productName ? (
                  <p className="text-xs text-destructive">{errors.productName}</p>
                ) : null}
              </div>
              <div className="grid gap-2">
                <Label>Image (optional)</Label>
                <ImageUpload
                  displayUrl={productImagePreviewUrl}
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
                  }}
                  disabled={isPending}
                />
              </div>
            </>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label>Slips</Label>
              <Input
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
            <div className="grid gap-2">
              <Label>Held back (not in slips)</Label>
              <Input
                type="number"
                min={0}
                value={heldBack}
                onChange={(e) => {
                  const raw = e.target.value;
                  if (raw === "") {
                    setHeldBack("");
                    return;
                  }
                  const v = parseInt(raw, 10);
                  if (!Number.isNaN(v) && v >= 0) {
                    setHeldBack(v);
                  }
                }}
                disabled={isPending}
                placeholder="0"
              />
              {errors.heldBack ? (
                <p className="text-xs text-destructive">{errors.heldBack}</p>
              ) : null}
            </div>
          </div>

          <div className="grid gap-2">
            <Label>Price (optional)</Label>
            <Input
              type="number"
              step="0.01"
              min={0}
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              disabled={isPending}
            />
            {errors.price ? (
              <p className="text-xs text-destructive">{errors.price}</p>
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
            Add tier
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
