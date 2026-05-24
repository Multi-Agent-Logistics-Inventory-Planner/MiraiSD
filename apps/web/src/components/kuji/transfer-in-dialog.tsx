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
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import {
  QuantityInput,
  type QuantityIntakeMeta,
} from "@/components/ui/quantity-input";
import { ProductLocationSelector } from "@/components/stock/product-location-selector";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import {
  useAddKujiSlipMutation,
  useTransferInMoreToKujiTierMutation,
  useTransferInInventoryOnlyToKujiTierMutation,
} from "@/hooks/mutations/use-kuji-box-mutations";
import { useProductInventoryEntries } from "@/hooks/queries/use-product-inventory-entries";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

export type TransferInMode = "with-slips" | "inventory-only" | "slips-only";

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
  const addSlip = useAddKujiSlipMutation();

  const isLinked = !!tier.linkedProductId;
  const effectiveInitialMode: TransferInMode = isLinked
    ? initialMode
    : "slips-only";

  const [mode, setMode] = useState<TransferInMode>(effectiveInitialMode);
  const [source, setSource] = useState<LocationSelection>(EMPTY_LOCATION);
  const [quantity, setQuantity] = useState<number | "">(1);
  // Tracks the most recent intake-unit choice from QuantityInput's toggle so we can
  // forward it into the audit-log metadata on submit.
  const [intakeMeta, setIntakeMeta] = useState<QuantityIntakeMeta>({
    unit: "pack",
    rawQty: 1,
  });
  const [errors, setErrors] = useState<{
    source?: string;
    quantity?: string;
  }>({});

  useEffect(() => {
    if (open) {
      setMode(isLinked ? initialMode : "slips-only");
      setSource(EMPTY_LOCATION);
      setQuantity(1);
      setIntakeMeta({ unit: "pack", rawQty: 1 });
      setErrors({});
    }
  }, [open, initialMode, isLinked]);

  const isAutoCreated = Boolean(tier.autoCreatedProduct);
  const inventoryQuery = useProductInventoryEntries(
    isAutoCreated || !isLinked ? null : tier.linkedProductId,
  );
  const availableEntries = (inventoryQuery.data?.entries ?? []).filter(
    (e) => e.locationId !== null && e.locationId !== box.locationId,
  );

  const isPending =
    transferInWithSlips.isPending ||
    transferInventoryOnly.isPending ||
    addSlip.isPending;

  const needsSource = mode !== "slips-only" && !isAutoCreated;

  const description =
    mode === "slips-only" ? (
      <>
        Add additional draw slips to{" "}
        <span className="font-medium text-foreground">{tier.label}</span>
        {tier.letter ? ` (${tier.letter})` : null}. This does not change
        linked-product inventory.
      </>
    ) : (
      <>
        Move inventory of the linked product into{" "}
        <span className="font-medium text-foreground">{tier.label}</span>
        {tier.letter ? ` (${tier.letter})` : null}.
      </>
    );

  const submitLabel =
    mode === "with-slips"
      ? "Transfer In"
      : mode === "inventory-only"
        ? "Transfer Inventory"
        : "Add Slip";

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    const nextErrors: { source?: string; quantity?: string } = {};
    if (needsSource && (!source.locationType || !source.locationId)) {
      nextErrors.source = "Source location is required";
    }
    if (quantity === "" || quantity < 1) {
      nextErrors.quantity = "Quantity must be at least 1";
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    if (quantity === "") return;

    try {
      if (mode === "slips-only") {
        await addSlip.mutateAsync({
          boxId: box.id,
          tierId: tier.id,
          productId: box.productId,
          payload: { actorId, quantity },
        });
        toast({ title: "Slip(s) added", variant: "success" });
      } else {
        if (needsSource && !source.locationId) return;
        const payload = {
          actorId,
          sourceLocationId: isAutoCreated ? null : source.locationId,
          quantity,
          intakeUnit: intakeMeta.unit === "box" ? ("box" as const) : undefined,
          intakeQty: intakeMeta.unit === "box" ? intakeMeta.rawQty : undefined,
        };
        const args = {
          boxId: box.id,
          tierId: tier.id,
          productId: box.productId,
          payload,
        };
        if (mode === "with-slips") {
          await transferInWithSlips.mutateAsync(args);
          toast({ title: "Transferred in", variant: "success" });
        } else {
          await transferInventoryOnly.mutateAsync(args);
          toast({ title: "Inventory transferred (no slips)", variant: "success" });
        }
      }
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to complete transfer";
      toast({ title: "Transfer failed", description: message });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Transfer</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        <fieldset className="grid gap-2" disabled={isPending}>
          <legend className="text-sm font-medium">Mode</legend>
          {isLinked ? (
            <>
              <ModeRadio
                id="transfer-mode-with-slips"
                name="transfer-mode"
                value="with-slips"
                checked={mode === "with-slips"}
                onChange={() => setMode("with-slips")}
                title="Add draw slips"
                description="Inventory is moved in and draw slips are added so prizes can be drawn."
              />
              <ModeRadio
                id="transfer-mode-inventory-only"
                name="transfer-mode"
                value="inventory-only"
                checked={mode === "inventory-only"}
                onChange={() => setMode("inventory-only")}
                title="Inventory only (no slips)"
                description="Move inventory without adding draw slips. Use when holding prizes back."
              />
            </>
          ) : (
            <p className="text-xs text-muted-foreground">
              This tier has no linked product, so only slip-count changes are
              available. Edit the tier to link a product if you also need to
              move inventory.
            </p>
          )}
          <ModeRadio
            id="transfer-mode-slips-only"
            name="transfer-mode"
            value="slips-only"
            checked={mode === "slips-only"}
            onChange={() => setMode("slips-only")}
            title="Add slip only"
            description="Add draw slips without moving inventory. Use to correct slip counts."
          />
        </fieldset>

        {needsSource ? (
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
        ) : null}

        <div className="grid gap-2">
          <Label htmlFor="transfer-qty">Quantity</Label>
          <QuantityInput
            value={quantity}
            onChange={(v) => {
              setQuantity(v);
              if (v !== "") {
                setErrors((prev) => ({ ...prev, quantity: undefined }));
              }
            }}
            min={1}
            disabled={isPending}
            packsPerBox={
              mode === "slips-only"
                ? null
                : (tier.linkedProductPacksPerBox ?? null)
            }
            onIntakeMetaChange={setIntakeMeta}
            layout="stacked"
          />
          {errors.quantity ? (
            <p className="text-xs text-destructive">{errors.quantity}</p>
          ) : null}
        </div>

        <DialogFooter>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isPending}
            className="w-full sm:w-auto"
          >
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {submitLabel}
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
