"use client";

import { useMemo, useState } from "react";
import { Loader2, Minus, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useRecordKujiDrawMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { DrawLine, KujiBox, KujiBoxTier } from "@/types/api";
import { compareTiers } from "./tier-palette";
import { SelectTierDialog } from "./select-tier-dialog";

interface InlineRecordDrawFormProps {
  readonly box: KujiBox;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}

interface DraftLine {
  readonly key: string;
  tierId: string;
  quantity: number;
}

let nextKey = 0;
function makeKey(): string {
  nextKey += 1;
  return `line-${nextKey}`;
}

type PickerMode =
  | { kind: "closed" }
  | { kind: "add" }
  | { kind: "swap"; lineKey: string };

export function InlineRecordDrawForm({
  box,
  open,
  onOpenChange,
}: InlineRecordDrawFormProps) {
  const onClose = () => onOpenChange(false);
  const { toast } = useToast();
  const { user } = useAuth();
  const recordDraw = useRecordKujiDrawMutation();

  const sortedTiers = useMemo(
    () => [...box.tiers].sort(compareTiers),
    [box.tiers],
  );

  const drawableTiers = useMemo(
    () => sortedTiers.filter((t) => t.activeCount > 0),
    [sortedTiers],
  );

  const [lines, setLines] = useState<DraftLine[]>(() => {
    const first = sortedTiers.find((t) => t.activeCount > 0);
    if (!first) return [];
    return [{ key: makeKey(), tierId: first.id, quantity: 1 }];
  });
  const [error, setError] = useState<string | null>(null);
  const [picker, setPicker] = useState<PickerMode>({ kind: "closed" });

  function updateLine(key: string, patch: Partial<Omit<DraftLine, "key">>) {
    setError(null);
    setLines((prev) =>
      prev.map((l) => (l.key === key ? { ...l, ...patch } : l)),
    );
  }

  function removeLine(key: string) {
    setLines((prev) => prev.filter((l) => l.key !== key));
  }

  function handleTierPicked(tier: KujiBoxTier) {
    if (picker.kind === "add") {
      setLines((prev) => [
        ...prev,
        { key: makeKey(), tierId: tier.id, quantity: 1 },
      ]);
    } else if (picker.kind === "swap") {
      updateLine(picker.lineKey, { tierId: tier.id, quantity: 1 });
    }
    setPicker({ kind: "closed" });
  }

  const tierById = useMemo(() => {
    const map = new Map<string, KujiBoxTier>();
    for (const t of sortedTiers) map.set(t.id, t);
    return map;
  }, [sortedTiers]);

  const totalDrawn = lines.reduce((sum, l) => sum + (l.quantity || 0), 0);

  const usedTierIds = useMemo(() => lines.map((l) => l.tierId), [lines]);

  function validate(): string | null {
    if (lines.length === 0) return "Add at least one prize.";
    for (const l of lines) {
      const tier = tierById.get(l.tierId);
      if (!tier) return "Invalid tier.";
      if (!l.quantity || l.quantity < 1) {
        return `Quantity for "${tier.label}" must be at least 1.`;
      }
      if (l.quantity > tier.activeCount) {
        return `"${tier.label}" only has ${tier.activeCount} slip${tier.activeCount === 1 ? "" : "s"} remaining.`;
      }
    }
    return null;
  }

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const draws: DrawLine[] = lines.map((l) => ({
      tierId: l.tierId,
      quantity: l.quantity,
    }));

    try {
      await recordDraw.mutateAsync({
        boxId: box.id,
        productId: box.productId,
        payload: {
          actorId,
          notes: null,
          draws,
        },
      });
      toast({ title: "Draw recorded", variant: "success" });
      setLines([]);
      setError(null);
      onClose();
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to record draw";
      setError(message);
    }
  }

  const isPending = recordDraw.isPending;

  if (drawableTiers.length === 0) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Record a draw</DialogTitle>
          </DialogHeader>
          <p className="text-xs text-muted-foreground">
            No tiers have remaining slips to draw.
          </p>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
            >
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  const canAddMore = lines.length < drawableTiers.length;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Record a draw</DialogTitle>
          </DialogHeader>

          <div className="space-y-2">
            {lines.map((line) => {
              const tier = tierById.get(line.tierId);
              const max = tier?.activeCount ?? 1;
              const qty = line.quantity || 0;
              return (
                <div key={line.key} className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      setPicker({ kind: "swap", lineKey: line.key })
                    }
                    disabled={isPending}
                    className="min-w-0 flex-1 overflow-hidden rounded-md border px-3 py-2 text-left hover:bg-muted/50 transition-colors disabled:opacity-50"
                  >
                    {tier ? (
                      (() => {
                        const productName = tier.linkedProductName?.trim();
                        const label = tier.label?.trim() ?? "";
                        const hasProduct =
                          !!productName &&
                          productName.toLowerCase() !== label.toLowerCase();
                        const primary = hasProduct ? productName : label;
                        const secondary = hasProduct ? label : null;
                        return (
                          <div className="min-w-0">
                            <div className="truncate text-sm">
                              {tier.letter ? (
                                <span className="mr-1.5 font-mono text-muted-foreground">
                                  {tier.letter}
                                </span>
                              ) : null}
                              {primary}
                            </div>
                            {secondary ? (
                              <div className="truncate text-xs text-muted-foreground">
                                {secondary}
                              </div>
                            ) : null}
                          </div>
                        );
                      })()
                    ) : (
                      <div className="truncate text-sm text-muted-foreground">
                        Select prize…
                      </div>
                    )}
                  </button>

                  <div className="flex items-center shrink-0">
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      className="h-9 w-9 rounded-r-none border-r-0"
                      onClick={() =>
                        updateLine(line.key, {
                          quantity: Math.max(1, qty - 1),
                        })
                      }
                      disabled={isPending || qty <= 1}
                      aria-label="Decrease quantity"
                    >
                      <Minus className="h-3 w-3" />
                    </Button>
                    <div className="h-9 w-10 flex items-center justify-center border-y text-sm tabular-nums">
                      {qty}
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      className="h-9 w-9 rounded-l-none border-l-0"
                      onClick={() =>
                        updateLine(line.key, {
                          quantity: Math.min(max, qty + 1),
                        })
                      }
                      disabled={isPending || qty >= max}
                      aria-label="Increase quantity"
                    >
                      <Plus className="h-3 w-3" />
                    </Button>
                  </div>

                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-9 w-9 shrink-0"
                    onClick={() => removeLine(line.key)}
                    disabled={isPending || lines.length <= 1}
                    aria-label="Remove line"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              );
            })}
          </div>

          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setPicker({ kind: "add" })}
            disabled={isPending || !canAddMore}
            className="w-full border-dashed"
          >
            <Plus className="mr-1 h-3.5 w-3.5" />
            Add another prize
          </Button>

          {error ? (
            <p className="text-xs text-destructive">{error}</p>
          ) : null}

          <DialogFooter className="flex flex-row items-center justify-between gap-2 border-t pt-3 sm:justify-between">
            <span className="text-xs text-muted-foreground">
              Total draws:{" "}
              <span className="font-medium tabular-nums text-foreground">
                {totalDrawn}
              </span>
            </span>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                disabled={isPending}
              >
                Cancel
              </Button>
              <Button
                type="button"
                size="sm"
                onClick={handleSubmit}
                disabled={isPending || totalDrawn <= 0}
              >
                {isPending ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : null}
                Confirm draw
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <SelectTierDialog
        open={picker.kind !== "closed"}
        onOpenChange={(o) => {
          if (!o) setPicker({ kind: "closed" });
        }}
        tiers={sortedTiers}
        excludeTierIds={picker.kind === "add" ? usedTierIds : []}
        onSelect={handleTierPicked}
      />
    </>
  );
}
