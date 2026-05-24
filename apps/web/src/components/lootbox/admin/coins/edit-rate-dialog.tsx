"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "@/hooks/use-toast";
import { useUpdateCoinEconomyConfigMutation } from "@/hooks/mutations/use-lootbox-mutations";

interface EditRateDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly currentRate: number | null;
  readonly hint: string | null;
}

/** Editor for the review-to-coin rate, rendered as a small modal. */
export function EditRateDialog({ open, onOpenChange, currentRate, hint }: EditRateDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit review coin rate</DialogTitle>
        </DialogHeader>
        {open ? (
          <EditRateDialogBody
            key={String(currentRate ?? "none")}
            currentRate={currentRate}
            hint={hint}
            onClose={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function EditRateDialogBody({
  currentRate,
  hint,
  onClose,
}: {
  readonly currentRate: number | null;
  readonly hint: string | null;
  readonly onClose: () => void;
}) {
  const updateMut = useUpdateCoinEconomyConfigMutation();
  const [rate, setRate] = useState<string>(
    currentRate !== null ? String(currentRate) : ""
  );

  const parsed = Number.parseInt(rate, 10);
  const valid = Number.isFinite(parsed) && parsed >= 0;
  const changed = currentRate !== null && parsed !== currentRate;
  const canSubmit = valid && changed && !updateMut.isPending;

  const submit = async () => {
    if (!canSubmit) return;
    try {
      await updateMut.mutateAsync({ reviewCoinRate: parsed });
      toast({ title: `Review coin rate set to ${parsed}.`, variant: "success" });
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update rate.";
      toast({ title: "Couldn't update rate", description: message });
    }
  };

  return (
    <>
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="review-coin-rate">Coins per 5-star review</Label>
          <Input
            id="review-coin-rate"
            type="number"
            min="0"
            step="1"
            value={rate}
            onChange={(e) => setRate(e.target.value)}
          />
          {!valid ? (
            <p className="text-xs text-rose-500">Rate must be a non-negative integer.</p>
          ) : null}
        </div>
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
      </div>
      <DialogFooter>
        <Button variant="ghost" onClick={onClose} disabled={updateMut.isPending}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!canSubmit}>
          {updateMut.isPending ? "Saving…" : "Save rate"}
        </Button>
      </DialogFooter>
    </>
  );
}
