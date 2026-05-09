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
import {
  useTransferInMoreToKujiTierMutation,
  useTransferInInventoryOnlyToKujiTierMutation,
} from "@/hooks/mutations/use-kuji-box-mutations";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import { cn } from "@/lib/utils";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

export type TransferInMode = "with-slips" | "inventory-only";

interface TransferInDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
  readonly tier: KujiBoxTier;
  readonly initialMode?: TransferInMode;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

export function TransferInDialog({
  open,
  onOpenChange,
  box,
  tier,
  initialMode = "with-slips",
}: TransferInDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const transferInWithSlips = useTransferInMoreToKujiTierMutation();
  const transferInventoryOnly = useTransferInInventoryOnlyToKujiTierMutation();

  const [mode, setMode] = useState<TransferInMode>(initialMode);
  const [source, setSource] = useState<LocationSelection>(EMPTY_LOCATION);
  const [quantity, setQuantity] = useState<number | "">(1);
  const [errors, setErrors] = useState<{
    source?: string;
    quantity?: string;
  }>({});

  useEffect(() => {
    if (open) {
      setMode(initialMode);
      setSource(EMPTY_LOCATION);
      setQuantity(1);
      setErrors({});
    }
  }, [open, initialMode]);

  const isLinked = !!tier.linkedProductId;
  const isAutoCreated = Boolean(tier.autoCreatedProduct);
  const inventoryQuery = useProductInventoryEntries(
    isAutoCreated ? null : tier.linkedProductId,
  );
  const availableEntries = (inventoryQuery.data?.entries ?? []).filter(
    (e) => e.locationId !== null && e.locationId !== box.locationId,
  );

  const isPending =
    transferInWithSlips.isPending || transferInventoryOnly.isPending;

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

    const payload = {
      actorId,
      sourceLocationId: isAutoCreated ? null : source.locationId,
      quantity,
    };
    const args = {
      boxId: box.id,
      tierId: tier.id,
      productId: box.productId,
      payload,
    };

    try {
      if (mode === "with-slips") {
        await transferInWithSlips.mutateAsync(args);
        toast({ title: "Transferred in", variant: "success" });
      } else {
        await transferInventoryOnly.mutateAsync(args);
        toast({ title: "Inventory transferred (no slips)", variant: "success" });
      }
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to transfer in";
      toast({ title: "Transfer-in failed", description: message });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Transfer Inventory In</DialogTitle>
          <DialogDescription>
            Move inventory of the linked product into{" "}
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
            <fieldset className="grid gap-2" disabled={isPending}>
              <legend className="text-sm font-medium">Mode</legend>
              <ModeRadio
                id="transfer-in-mode-with-slips"
                name="transfer-in-mode"
                value="with-slips"
                checked={mode === "with-slips"}
                onChange={() => setMode("with-slips")}
                title="Add draw slips"
                description="Inventory is moved in and draw slips are added so prizes can be drawn."
              />
              <ModeRadio
                id="transfer-in-mode-inventory-only"
                name="transfer-in-mode"
                value="inventory-only"
                checked={mode === "inventory-only"}
                onChange={() => setMode("inventory-only")}
                title="Inventory only (no slips)"
                description="Move inventory without adding draw slips. Use when holding prizes back."
              />
            </fieldset>

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
            {mode === "with-slips" ? "Transfer In" : "Transfer Inventory"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface ModeRadioProps {
  readonly id: string;
  readonly name: string;
  readonly value: string;
  readonly checked: boolean;
  readonly onChange: () => void;
  readonly title: string;
  readonly description: string;
}

function ModeRadio({
  id,
  name,
  value,
  checked,
  onChange,
  title,
  description,
}: ModeRadioProps) {
  return (
    <label
      htmlFor={id}
      className={cn(
        "flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm transition-colors",
        checked
          ? "border-primary bg-primary/5"
          : "border-input hover:bg-accent/50",
      )}
    >
      <input
        id={id}
        type="radio"
        name={name}
        value={value}
        checked={checked}
        onChange={onChange}
        className="mt-0.5 h-4 w-4 accent-primary"
      />
      <span className="grid gap-0.5">
        <span className="font-medium">{title}</span>
        <span className="text-xs text-muted-foreground">{description}</span>
      </span>
    </label>
  );
}
