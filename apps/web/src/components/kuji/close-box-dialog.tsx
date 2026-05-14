"use client";

import { useEffect, useMemo, useState } from "react";
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
import { Label } from "@/components/ui/label";
import { LocationSelector } from "@/components/stock/location-selector";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useCloseKujiBoxMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type {
  CloseKujiBoxTransferOutTarget,
  KujiBox,
} from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

interface CloseBoxDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

export function CloseBoxDialog({
  open,
  onOpenChange,
  box,
}: CloseBoxDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const closeBox = useCloseKujiBoxMutation();

  // Tiers backed by a pre-existing product with leftover inventory need a destination.
  // Auto-created tiers are scoped to this box: at close, the backend zeros out
  // their inventory and soft-deletes the product — no destination required.
  const tiersNeedingDestination = useMemo(
    () =>
      box.tiers.filter(
        (t) =>
          !!t.linkedProductId &&
          !t.autoCreatedProduct &&
          (t.activeCount + t.inactiveCount) > 0,
      ),
    [box.tiers],
  );

  const tiersWithoutInventory = useMemo(
    () =>
      box.tiers.filter(
        (t) =>
          !!t.linkedProductId &&
          !t.autoCreatedProduct &&
          (t.activeCount + t.inactiveCount) <= 0,
      ),
    [box.tiers],
  );

  const autoCreatedTiers = useMemo(
    () => box.tiers.filter((t) => Boolean(t.autoCreatedProduct)),
    [box.tiers],
  );

  const [destinations, setDestinations] = useState<
    Record<string, LocationSelection>
  >({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) {
      setDestinations({});
      setErrors({});
    }
  }, [open]);

  function handleDestChange(tierId: string, value: LocationSelection) {
    setDestinations((prev) => ({ ...prev, [tierId]: value }));
    setErrors((prev) => {
      if (!prev[tierId]) return prev;
      const next = { ...prev };
      delete next[tierId];
      return next;
    });
  }

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    const nextErrors: Record<string, string> = {};
    const transferOutTargets: CloseKujiBoxTransferOutTarget[] = [];
    for (const tier of tiersNeedingDestination) {
      const dest = destinations[tier.id];
      if (!dest?.locationType || !dest.locationId) {
        nextErrors[tier.id] = "Destination is required";
        continue;
      }
      transferOutTargets.push({
        tierId: tier.id,
        destinationLocationId: dest.locationId,
      });
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    try {
      await closeBox.mutateAsync({
        boxId: box.id,
        productId: box.productId,
        payload: {
          actorId,
          transferOutTargets:
            transferOutTargets.length > 0 ? transferOutTargets : undefined,
        },
      });
      toast({ title: "Box closed", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to close box";
      toast({ title: "Close failed", description: message });
    }
  }

  const isPending = closeBox.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="p-6 pb-2">
          <DialogTitle>Close Kuji Box</DialogTitle>
          <DialogDescription>
            Pick a destination for any linked-product inventory still held at
            the box&apos;s location.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 pb-4 space-y-4">
          {tiersNeedingDestination.length === 0 &&
          tiersWithoutInventory.length === 0 &&
          autoCreatedTiers.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No linked tiers — nothing to transfer out.
            </p>
          ) : null}

          {tiersNeedingDestination.length > 0 ? (
            <div className="space-y-3">
              <div className="text-xs font-medium uppercase text-muted-foreground">
                Needs destination
              </div>
              {tiersNeedingDestination.map((tier) => (
                <div key={tier.id} className="grid gap-2 border rounded-md p-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="font-medium truncate">
                      <span className="font-mono mr-2">
                        {tier.letter ?? "—"}
                      </span>
                      {tier.label}
                    </div>
                    <div className="text-xs text-muted-foreground shrink-0">
                      {(tier.activeCount + tier.inactiveCount).toLocaleString()}{" "}
                      remaining
                    </div>
                  </div>
                  <Label className="text-xs text-muted-foreground">
                    Destination for {tier.linkedProductName ?? "linked product"}
                  </Label>
                  <LocationSelector
                    label=""
                    value={destinations[tier.id] ?? EMPTY_LOCATION}
                    onChange={(v) => handleDestChange(tier.id, v)}
                    disabled={isPending}
                    excludeDisplayOnly
                  />
                  {errors[tier.id] ? (
                    <p className="text-xs text-destructive">{errors[tier.id]}</p>
                  ) : null}
                </div>
              ))}
            </div>
          ) : null}

          {tiersWithoutInventory.length > 0 ? (
            <div className="space-y-2">
              <div className="text-xs font-medium uppercase text-muted-foreground">
                No inventory to transfer
              </div>
              {tiersWithoutInventory.map((tier) => (
                <div
                  key={tier.id}
                  className="flex items-center justify-between border rounded-md px-3 py-2 text-sm"
                >
                  <span className="truncate">
                    <span className="font-mono mr-2">
                      {tier.letter ?? "—"}
                    </span>
                    {tier.label}
                  </span>
                  <span className="text-xs text-muted-foreground">—</span>
                </div>
              ))}
            </div>
          ) : null}

          {autoCreatedTiers.length > 0 ? (
            <div className="space-y-2">
              <div className="text-xs font-medium uppercase text-muted-foreground">
                Will be removed
              </div>
              <p className="text-xs text-muted-foreground">
                Auto-created prizes are scoped to this box. Any leftover stock
                will be zeroed out, the product set inactive, and its image
                deleted.
              </p>
              {autoCreatedTiers.map((tier) => (
                <div
                  key={tier.id}
                  className="flex items-center justify-between border rounded-md px-3 py-2 text-sm"
                >
                  <span className="truncate">
                    <span className="font-mono mr-2">
                      {tier.letter ?? "—"}
                    </span>
                    {tier.label}
                  </span>
                  <span className="text-xs text-muted-foreground shrink-0">
                    {(tier.activeCount + tier.inactiveCount).toLocaleString()}{" "}
                    leftover
                  </span>
                </div>
              ))}
            </div>
          ) : null}
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
            Close Box
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
