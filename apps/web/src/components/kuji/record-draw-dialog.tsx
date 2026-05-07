"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
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
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useRecordKujiDrawMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { DrawLine, KujiBox } from "@/types/api";
import { compareTiers } from "./tier-palette";

interface RecordDrawDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
}

export function RecordDrawDialog({
  open,
  onOpenChange,
  box,
}: RecordDrawDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const recordDraw = useRecordKujiDrawMutation();

  const sortedTiers = useMemo(
    () => [...box.tiers].sort(compareTiers),
    [box.tiers],
  );

  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) {
      setQuantities({});
      setNotes("");
      setErrors({});
    }
  }, [open]);

  function handleQtyChange(tierId: string, raw: string) {
    setErrors((prev) => {
      if (!prev[tierId]) return prev;
      const next = { ...prev };
      delete next[tierId];
      return next;
    });
    if (raw === "") {
      setQuantities((prev) => {
        const next = { ...prev };
        delete next[tierId];
        return next;
      });
      return;
    }
    const v = parseInt(raw, 10);
    if (Number.isNaN(v) || v < 0) return;
    setQuantities((prev) => ({ ...prev, [tierId]: v }));
  }

  const totalDrawn = useMemo(
    () => Object.values(quantities).reduce((sum, q) => sum + (q ?? 0), 0),
    [quantities],
  );

  function validate(): boolean {
    const nextErrors: Record<string, string> = {};
    for (const tier of sortedTiers) {
      const qty = quantities[tier.id] ?? 0;
      if (qty < 0) {
        nextErrors[tier.id] = "Quantity must be ≥ 0";
        continue;
      }
      if (qty > tier.count) {
        nextErrors[tier.id] = `Exceeds remaining count (${tier.count})`;
      }
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }

    if (totalDrawn <= 0) {
      toast({
        title: "No draws entered",
        description: "Enter a quantity for at least one tier.",
      });
      return;
    }

    if (!validate()) return;

    const draws: DrawLine[] = sortedTiers
      .map((tier) => ({ tierId: tier.id, quantity: quantities[tier.id] ?? 0 }))
      .filter((d) => d.quantity > 0);

    try {
      await recordDraw.mutateAsync({
        boxId: box.id,
        productId: box.productId,
        payload: {
          actorId,
          notes: notes.trim() || null,
          draws,
        },
      });
      toast({ title: "Draw recorded", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to record draw";
      toast({ title: "Record draw failed", description: message });
    }
  }

  const isPending = recordDraw.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-lg max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="p-6 pb-2">
          <DialogTitle>Record Draw</DialogTitle>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 pb-4 space-y-4">
          <div className="space-y-2">
            {sortedTiers.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                This box has no tiers.
              </p>
            ) : (
              sortedTiers.map((tier) => (
                <div
                  key={tier.id}
                  className="flex items-center gap-3 py-2 border-b last:border-b-0"
                >
                  <div className="flex-1 min-w-0">
                    <div className="font-medium truncate">
                      <span className="font-mono mr-2">
                        {tier.letter ?? "—"}
                      </span>
                      {tier.label}
                    </div>
                    <div className="text-xs text-muted-foreground">
                      {tier.count.toLocaleString()} remaining
                    </div>
                    {errors[tier.id] ? (
                      <p className="text-xs text-destructive mt-1">
                        {errors[tier.id]}
                      </p>
                    ) : null}
                  </div>
                  <Input
                    type="number"
                    min={0}
                    max={tier.count}
                    className="w-20 text-right [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                    value={quantities[tier.id] ?? ""}
                    placeholder="0"
                    disabled={isPending}
                    onChange={(e) => handleQtyChange(tier.id, e.target.value)}
                  />
                </div>
              ))
            )}
          </div>

          <div className="grid gap-2">
            <Label htmlFor="record-draw-notes">
              Notes{" "}
              <span className="text-muted-foreground font-normal">
                (optional)
              </span>
            </Label>
            <textarea
              id="record-draw-notes"
              className="border-input bg-background min-h-[60px] w-full rounded-md border px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              disabled={isPending}
              placeholder="Optional notes"
            />
          </div>

          <div className="flex items-center justify-between border-t pt-3 text-sm">
            <span className="text-muted-foreground">Total draws</span>
            <span className="font-medium tabular-nums">{totalDrawn}</span>
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
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isPending || totalDrawn <= 0}
          >
            {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Record Draw
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
