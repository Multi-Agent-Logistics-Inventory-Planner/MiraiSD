"use client";

import { useEffect, useState } from "react";
import { Copy, Layers, Loader2, Plus } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { LocationSelector } from "@/components/stock/location-selector";
import { isUploadError, uploadProductImage } from "@/lib/supabase/storage";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useOpenKujiBoxMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useLastClosedKujiTiers } from "@/hooks/queries/use-kuji-box";
import type { NewKujiBoxTier, Product } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";
import {
  blankTier,
  EMPTY_LOCATION,
  validateTier,
  type DraftTier,
} from "@/components/kuji/tier-draft";
import {
  TierPanel,
  TierSidebarItem,
} from "@/components/kuji/tier-draft-ui";

interface OpenBoxDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly productId: string;
  readonly productName: string;
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
  const lastTiersQuery = useLastClosedKujiTiers(open ? productId : null);

  const [boxLocation, setBoxLocation] =
    useState<LocationSelection>(EMPTY_LOCATION);
  const [tiers, setTiers] = useState<DraftTier[]>([]);
  const [activeTempId, setActiveTempId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (open) {
      const seed = blankTier();
      setBoxLocation(EMPTY_LOCATION);
      setTiers([seed]);
      setActiveTempId(seed.tempId);
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
    const cloned: DraftTier[] = data.map((t) => ({
      tempId: crypto.randomUUID(),
      label: t.label,
      letter: t.letter ?? "",
      mode: t.linkedProductId ? "existing" : "create",
      linkedProductId: t.linkedProductId ?? "",
      linkedProductDisplayName: t.linkedProductName ?? "",
      linkedProductDisplaySku: null,
      source: EMPTY_LOCATION,
      productName: "",
      productImageFile: null,
      productImagePreviewUrl: null,
      productImageUrl: "",
      count: t.count,
      heldBack: "",
      price: t.price != null ? String(t.price) : "",
    }));
    setTiers(cloned);
    setActiveTempId(cloned[0]?.tempId ?? null);
    toast({ title: `Cloned ${data.length} tier(s)`, variant: "success" });
  }

  function handleAddTier() {
    const next = blankTier();
    setTiers((prev) => [...prev, next]);
    setActiveTempId(next.tempId);
  }

  function handleRemoveTier(tempId: string) {
    setTiers((prev) => {
      if (prev.length <= 1) return prev;
      const idx = prev.findIndex((t) => t.tempId === tempId);
      const next = prev.filter((t) => t.tempId !== tempId);
      if (activeTempId === tempId) {
        const fallback = next[idx] ?? next[idx - 1] ?? next[0];
        setActiveTempId(fallback?.tempId ?? null);
      }
      return next;
    });
  }

  function updateTier(tempId: string, updates: Partial<DraftTier>) {
    setTiers((prev) =>
      prev.map((t) => (t.tempId === tempId ? { ...t, ...updates } : t)),
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

  function handleLinkedProductSelect(tempId: string, product: Product) {
    setTiers((prev) =>
      prev.map((t) => {
        if (t.tempId !== tempId) return t;
        return {
          ...t,
          linkedProductId: product.id,
          linkedProductDisplayName: product.name,
          linkedProductDisplaySku: product.sku ?? null,
          source: EMPTY_LOCATION,
          price: product.msrp != null ? String(product.msrp) : t.price,
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
    tiers.forEach((tier) => validateTier(tier, nextErrors));
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      // Jump to the first tier with an error
      const firstBadTier = tiers.find((t) =>
        Object.keys(nextErrors).some((k) => k.startsWith(`tier:${t.tempId}:`)),
      );
      if (firstBadTier) setActiveTempId(firstBadTier.tempId);
    }
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
  const activeTier = tiers.find((t) => t.tempId === activeTempId) ?? null;
  const activeIndex = activeTier
    ? tiers.findIndex((t) => t.tempId === activeTier.tempId)
    : -1;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="px-6 py-4 border-b">
          <DialogTitle>Open Kuji Box for {productName}</DialogTitle>
          <DialogDescription>
            Configure the location and tier list for the new kuji box.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-1 min-h-0 overflow-hidden">
          <aside className="w-[220px] shrink-0 bg-muted/40 border-r flex flex-col overflow-hidden">
            <div className="p-3 border-b">
              <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                Box location
              </Label>
              <div className="mt-1.5">
                <LocationSelector
                  label=""
                  value={boxLocation}
                  onChange={setBoxLocation}
                  disabled={isPending}
                  excludeDisplayOnly
                />
              </div>
              {errors.boxLocation ? (
                <p className="text-xs text-destructive mt-1">
                  {errors.boxLocation}
                </p>
              ) : null}
            </div>

            <div className="flex items-center justify-between px-3 pt-3 pb-1.5">
              <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                Tiers
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-6 px-2 text-xs text-muted-foreground"
                onClick={handleCloneFromLast}
                disabled={
                  isPending || lastTiersQuery.isLoading || !hasLastTiers
                }
                title={
                  hasLastTiers
                    ? "Clone tiers from last closed box"
                    : "No previous box to clone"
                }
              >
                <Copy className="h-3 w-3 mr-1" />
                Clone last
              </Button>
            </div>

            <div className="flex-1 overflow-y-auto px-2 pb-2">
              {tiers.map((tier, idx) => (
                <TierSidebarItem
                  key={tier.tempId}
                  tier={tier}
                  index={idx}
                  active={tier.tempId === activeTempId}
                  canRemove={tiers.length > 1}
                  disabled={isPending}
                  onSelect={() => setActiveTempId(tier.tempId)}
                  onRemove={() => handleRemoveTier(tier.tempId)}
                />
              ))}
              {errors.tiers ? (
                <p className="text-xs text-destructive px-2 py-2">
                  {errors.tiers}
                </p>
              ) : null}
            </div>

            <button
              type="button"
              onClick={handleAddTier}
              disabled={isPending}
              className="flex items-center justify-center gap-1.5 px-3 py-2.5 border-t text-sm text-muted-foreground hover:text-foreground hover:bg-muted/60 disabled:opacity-50 transition-colors"
            >
              <Plus className="h-4 w-4" />
              Add tier
            </button>
          </aside>

          <div className="flex-1 overflow-y-auto min-w-0">
            {activeTier ? (
              <TierPanel
                tier={activeTier}
                index={activeIndex}
                errors={errors}
                isPending={isPending}
                onUpdate={updateTier}
                onLinkedProductSelect={handleLinkedProductSelect}
              />
            ) : (
              <div className="h-full flex flex-col items-center justify-center gap-2 text-muted-foreground p-8">
                <Layers className="h-8 w-8 opacity-40" />
                <span className="text-sm">Select a tier to configure it</span>
              </div>
            )}
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
