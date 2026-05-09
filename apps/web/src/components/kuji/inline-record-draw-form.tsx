"use client";

import { useMemo, useState } from "react";
import { Loader2, Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useRecordKujiDrawMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { DrawLine, KujiBox, KujiBoxTier } from "@/types/api";
import { compareTiers } from "./tier-palette";
import { TierName } from "./tier-name";

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
    () => sortedTiers.filter((t) => t.count > 0),
    [sortedTiers],
  );

  const [lines, setLines] = useState<DraftLine[]>(() => {
    const first = sortedTiers.find((t) => t.count > 0);
    if (!first) return [];
    return [{ key: makeKey(), tierId: first.id, quantity: 1 }];
  });
  const [error, setError] = useState<string | null>(null);

  function updateLine(key: string, patch: Partial<Omit<DraftLine, "key">>) {
    setError(null);
    setLines((prev) =>
      prev.map((l) => (l.key === key ? { ...l, ...patch } : l)),
    );
  }

  function addLine() {
    const used = new Set(lines.map((l) => l.tierId));
    const next = drawableTiers.find((t) => !used.has(t.id)) ?? drawableTiers[0];
    if (!next) return;
    setLines((prev) => [
      ...prev,
      { key: makeKey(), tierId: next.id, quantity: 1 },
    ]);
  }

  function removeLine(key: string) {
    setLines((prev) => prev.filter((l) => l.key !== key));
  }

  const tierById = useMemo(() => {
    const map = new Map<string, KujiBoxTier>();
    for (const t of sortedTiers) map.set(t.id, t);
    return map;
  }, [sortedTiers]);

  const totalDrawn = lines.reduce((sum, l) => sum + (l.quantity || 0), 0);

  function validate(): string | null {
    if (lines.length === 0) return "Add at least one prize.";
    for (const l of lines) {
      const tier = tierById.get(l.tierId);
      if (!tier) return "Invalid tier.";
      if (!l.quantity || l.quantity < 1) {
        return `Quantity for "${tier.label}" must be at least 1.`;
      }
      if (l.quantity > tier.count) {
        return `"${tier.label}" only has ${tier.count} slip${tier.count === 1 ? "" : "s"} remaining.`;
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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Record a draw</DialogTitle>
        </DialogHeader>

        <div className="space-y-2">
        {lines.map((line) => {
          const tier = tierById.get(line.tierId);
          const max = tier?.count ?? 1;
          return (
            <div key={line.key} className="flex items-center gap-2">
              <Select
                value={line.tierId}
                onValueChange={(v) => updateLine(line.key, { tierId: v })}
                disabled={isPending}
              >
                <SelectTrigger className="flex-1 min-w-0">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {drawableTiers.map((t) => (
                    <SelectItem key={t.id} value={t.id}>
                      <TierName tier={t} className="text-sm" />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Input
                type="number"
                min={1}
                max={max}
                className="w-20 text-right [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                value={line.quantity || ""}
                disabled={isPending}
                onChange={(e) => {
                  const v = parseInt(e.target.value, 10);
                  updateLine(line.key, {
                    quantity: Number.isNaN(v) ? 0 : v,
                  });
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-9 w-9 shrink-0"
                onClick={() => removeLine(line.key)}
                disabled={isPending || lines.length <= 1}
                aria-label="Remove line"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          );
        })}
      </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={addLine}
          disabled={isPending || lines.length >= drawableTiers.length}
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
  );
}
