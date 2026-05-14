"use client";

import { useEffect, useRef, useState } from "react";
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
import { useOpenKujiBoxMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { NewKujiBoxTier } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";
import { EMPTY_LOCATION } from "@/components/kuji/tier-draft";
import {
  TierDraftEditor,
  type TierDraftEditorHandle,
} from "@/components/kuji/tier-draft-editor";

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

  const [boxLocation, setBoxLocation] =
    useState<LocationSelection>(EMPTY_LOCATION);
  const [locationError, setLocationError] = useState<string | null>(null);
  const editorRef = useRef<TierDraftEditorHandle>(null);

  useEffect(() => {
    if (open) {
      setBoxLocation(EMPTY_LOCATION);
      setLocationError(null);
    }
  }, [open]);

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }
    if (!boxLocation.locationType || !boxLocation.locationId) {
      setLocationError("Box location is required");
      return;
    }
    setLocationError(null);

    const processed = await editorRef.current?.submitTiers();
    if (!processed) return;

    const payloadTiers: NewKujiBoxTier[] = processed.map((t) => ({
      label: t.label,
      letter: t.letter,
      linkedProductId: t.linkedProductId,
      sourceLocationId: t.sourceLocationId,
      activeCount: t.activeCount,
      inactiveCount: t.inactiveCount,
      price: t.price,
      autoCreate: t.mode === "create",
      productName: t.productName,
      productImageUrl: t.productImageUrl,
      productMsrp: t.mode === "create" ? t.price : null,
    }));

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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="px-6 py-4 border-b">
          <DialogTitle>Open Kuji Box for {productName}</DialogTitle>
          <DialogDescription>
            Configure the location and tier list for the new kuji box.
          </DialogDescription>
        </DialogHeader>

        <TierDraftEditor
          ref={editorRef}
          productId={productId}
          isPending={isPending}
          resetSignal={open}
          sidebarHeader={
            <div className="p-3 border-b">
              <Label className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                Box location
              </Label>
              <div className="mt-1.5">
                <LocationSelector
                  label=""
                  value={boxLocation}
                  onChange={(v) => {
                    setBoxLocation(v);
                    if (v.locationId) setLocationError(null);
                  }}
                  disabled={isPending}
                  excludeDisplayOnly
                />
              </div>
              {locationError ? (
                <p className="text-xs text-destructive mt-1">{locationError}</p>
              ) : null}
            </div>
          }
        />

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
