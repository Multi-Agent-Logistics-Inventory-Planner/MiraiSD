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
import { ProductLocationSelector } from "@/components/stock/product-location-selector";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useTransferInMoreToKujiTierMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

interface TransferInMoreDialogProps {
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

export function TransferInMoreDialog({
  open,
  onOpenChange,
  box,
  tier,
}: TransferInMoreDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const transferIn = useTransferInMoreToKujiTierMutation();

  const [source, setSource] = useState<LocationSelection>(EMPTY_LOCATION);
  const [quantity, setQuantity] = useState<number | "">(1);
  const [errors, setErrors] = useState<{
    source?: string;
    quantity?: string;
  }>({});

  useEffect(() => {
    if (open) {
      setSource(EMPTY_LOCATION);
      setQuantity(1);
      setErrors({});
    }
  }, [open]);

  const isLinked = !!tier.linkedProductId;
  const isAutoCreated = Boolean(tier.autoCreatedProduct);
  const inventoryQuery = useProductInventoryEntries(
    isAutoCreated ? null : tier.linkedProductId,
  );
  const availableEntries = (inventoryQuery.data?.entries ?? []).filter(
    (e) => e.locationId !== null && e.locationId !== box.locationId,
  );

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    const nextErrors: { source?: string; quantity?: string } = {};
    if (!isAutoCreated && (!source.locationType || !source.locationId)) {
      nextErrors.source = "Source location is required";
    }
    if (quantity === "" || quantity < 1) {
      nextErrors.quantity = "Quantity must be at least 1";
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    if (quantity === "") return;
    if (!isAutoCreated && !source.locationId) return;

    try {
      await transferIn.mutateAsync({
        boxId: box.id,
        tierId: tier.id,
        productId: box.productId,
        payload: {
          actorId,
          sourceLocationId: isAutoCreated ? null : source.locationId,
          quantity,
        },
      });
      toast({ title: "Transferred in", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to transfer in";
      toast({ title: "Transfer-in failed", description: message });
    }
  }

  const isPending = transferIn.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Transfer-In More</DialogTitle>
          <DialogDescription>
            Move additional inventory of the linked product into{" "}
            <span className="font-medium text-foreground">{tier.label}</span>
            {tier.letter ? ` (${tier.letter})` : null}.
          </DialogDescription>
        </DialogHeader>

        {!isLinked ? (
          <div className="text-sm text-destructive">
            This tier has no linked product. Edit the tier first to link a
            product before transferring inventory in.
          </div>
        ) : (
          <>
            {isAutoCreated ? null : (
              <div className="grid gap-2">
                <Label>Source Location</Label>
                {inventoryQuery.isLoading ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Loading locations…
                  </div>
                ) : (
                  <ProductLocationSelector
                    inventoryEntries={availableEntries}
                    value={source}
                    onChange={(v) => {
                      setSource(v);
                      setErrors((prev) => ({ ...prev, source: undefined }));
                    }}
                    disabled={isPending}
                  />
                )}
                {errors.source ? (
                  <p className="text-xs text-destructive">{errors.source}</p>
                ) : null}
              </div>
            )}

            <div className="grid gap-2">
              <Label htmlFor="transfer-in-qty">Quantity</Label>
              <Input
                id="transfer-in-qty"
                type="number"
                min={1}
                value={quantity}
                onChange={(e) => {
                  const raw = e.target.value;
                  if (raw === "") {
                    setQuantity("");
                    return;
                  }
                  const v = parseInt(raw, 10);
                  if (!Number.isNaN(v) && v >= 1) {
                    setQuantity(v);
                    setErrors((prev) => ({ ...prev, quantity: undefined }));
                  }
                }}
                disabled={isPending}
              />
              {errors.quantity ? (
                <p className="text-xs text-destructive">{errors.quantity}</p>
              ) : null}
            </div>
          </>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isPending || !isLinked}
          >
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Transfer In
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
