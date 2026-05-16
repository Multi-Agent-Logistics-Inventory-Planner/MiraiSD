"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useRecordKujiDrawMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import type { DrawLine, KujiBox, KujiBoxTier } from "@/types/api";
import { compareTiers } from "./tier-palette";
import { DialogChrome, DialogCloseButton } from "./dialog-chrome";
import { ALL_FILTER, matchesClassFilter } from "./tier-class-chips";
import { PrizeSearchFilter } from "./prize-search-filter";
import { TierClassColorProvider } from "./tier-class-color-context";
import { TierTile } from "./tier-tile";
import { TierRow } from "./tier-row";

interface RecordDrawDialogProps {
  readonly box: KujiBox;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}

export function RecordDrawDialog({
  box,
  open,
  onOpenChange,
}: RecordDrawDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const recordDraw = useRecordKujiDrawMutation();

  const [counts, setCounts] = useState<Record<string, number>>({});
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<string>(ALL_FILTER);

  useEffect(() => {
    if (!open) {
      setCounts({});
      setQuery("");
      setFilter(ALL_FILTER);
    }
  }, [open]);

  const sortedTiers = useMemo(
    () => [...box.tiers].sort(compareTiers),
    [box.tiers],
  );

  const totalActive = useMemo(
    () => box.tiers.reduce((sum, t) => sum + t.activeCount, 0),
    [box.tiers],
  );

  const visibleTiers = useMemo(() => {
    const q = query.trim().toLowerCase();
    return sortedTiers.filter((t) => {
      if (t.activeCount <= 0) return false;
      if (!matchesClassFilter(t, filter)) return false;
      if (!q) return true;
      const label = (t.label ?? "").toLowerCase();
      const product = (t.linkedProductName ?? "").toLowerCase();
      return label.includes(q) || product.includes(q);
    });
  }, [sortedTiers, query, filter]);

  const totalSlips = useMemo(
    () => Object.values(counts).reduce((s, n) => s + n, 0),
    [counts],
  );

  const selectedCount = useMemo(
    () => Object.values(counts).filter((n) => n > 0).length,
    [counts],
  );

  const anyOverDraw = useMemo(() => {
    for (const tier of box.tiers) {
      const c = counts[tier.id] ?? 0;
      if (c > tier.activeCount) return true;
    }
    return false;
  }, [counts, box.tiers]);

  function setCount(tier: KujiBoxTier, next: number) {
    setCounts((prev) => {
      const copy = { ...prev };
      if (next <= 0) {
        delete copy[tier.id];
      } else {
        copy[tier.id] = next;
      }
      return copy;
    });
  }

  function bumpCount(tier: KujiBoxTier, delta: number) {
    const current = counts[tier.id] ?? 0;
    setCount(tier, current + delta);
  }

  async function handleSubmit() {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) {
      toast({ title: "Sign in required" });
      return;
    }
    if (totalSlips <= 0) return;
    if (anyOverDraw) return;

    const draws: DrawLine[] = Object.entries(counts)
      .filter(([, q]) => q > 0)
      .map(([tierId, quantity]) => ({ tierId, quantity }));

    try {
      await recordDraw.mutateAsync({
        boxId: box.id,
        productId: box.productId,
        payload: { actorId, notes: null, draws },
      });
      toast({ title: "Draw recorded", variant: "success" });
      onOpenChange(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to record draw";
      toast({
        title: "Record draw failed",
        description: message,
        variant: "destructive",
      });
    }
  }

  const isPending = recordDraw.isPending;
  const confirmDisabled = isPending || totalSlips <= 0 || anyOverDraw;
  const matchingCount = visibleTiers.length;
  const showingMatchSuffix =
    query.trim().length > 0 || filter !== ALL_FILTER ? "matching" : "in box";

  return (
    <DialogChrome open={open} onOpenChange={onOpenChange} variant="recordDraw">
     <TierClassColorProvider tiers={box.tiers}>
      <DialogHeader className="px-6 pt-5 pb-3.5 flex flex-row items-start gap-3 space-y-0">
        <div className="flex-1 min-w-0">
          <DialogTitle className="text-lg font-medium text-white">
            Record a draw
          </DialogTitle>
          <DialogDescription className="text-[12.5px] text-muted-foreground mt-0.5">
            Tap a prize to add it. Tap again to add more.
          </DialogDescription>
        </div>
        <DialogCloseButton onClose={() => onOpenChange(false)} />
      </DialogHeader>

      <div className="px-4 sm:px-6 pb-2 sm:pb-3">
        <PrizeSearchFilter
          tiers={box.tiers}
          totalActive={totalActive}
          query={query}
          onQueryChange={setQuery}
          filter={filter}
          onFilterChange={setFilter}
          autoFocus
        />
      </div>

      <div className="px-6 pb-2 flex items-center justify-between text-[11px] text-muted-foreground">
        <span>
          {matchingCount} {matchingCount === 1 ? "prize" : "prizes"}{" "}
          {showingMatchSuffix}
        </span>
        {selectedCount > 0 ? (
          <button
            type="button"
            onClick={() => setCounts({})}
            className="underline hover:text-foreground"
          >
            Clear selection
          </button>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto px-6 pb-4">
        {visibleTiers.length === 0 ? (
          <div className="py-10 text-center text-[13px] text-muted-foreground">
            No prizes match &ldquo;
            <span className="text-foreground/80">{query}</span>&rdquo;.
          </div>
        ) : (
          <>
            <div className="hidden sm:grid grid-cols-2 sm:grid-cols-3 gap-2">
              {visibleTiers.map((tier) => {
                const count = counts[tier.id] ?? 0;
                return (
                  <TierTile
                    key={tier.id}
                    mode="recordDraw"
                    tier={tier}
                    totalActive={totalActive}
                    count={count}
                    onBodyClick={() => bumpCount(tier, 1)}
                    onAddOne={() => bumpCount(tier, 1)}
                    onAddFive={() => bumpCount(tier, 5)}
                    onDecrement={() => bumpCount(tier, -1)}
                  />
                );
              })}
            </div>
            <div className="grid sm:hidden grid-cols-1 gap-2">
              {visibleTiers.map((tier) => {
                const count = counts[tier.id] ?? 0;
                return (
                  <TierRow
                    key={tier.id}
                    mode="recordDraw"
                    tier={tier}
                    totalActive={totalActive}
                    count={count}
                    onBodyClick={() => bumpCount(tier, 1)}
                    onAddOne={() => bumpCount(tier, 1)}
                    onAddFive={() => bumpCount(tier, 5)}
                    onAddTen={() => bumpCount(tier, 10)}
                    onDecrement={() => bumpCount(tier, -1)}
                  />
                );
              })}
            </div>
          </>
        )}
      </div>

      <div className="px-6 py-3.5 border-t border-border flex items-center gap-3">
        <div className="flex-1 text-[12.5px] text-muted-foreground">
          {totalSlips > 0 ? (
            <>
              Recording{" "}
              <span className="font-medium text-foreground tabular-nums">
                {totalSlips} slip{totalSlips === 1 ? "" : "s"}
              </span>
              {selectedCount > 1 ? (
                <>
                  {" across "}
                  <span className="text-foreground/80 tabular-nums">
                    {selectedCount} prizes
                  </span>
                </>
              ) : null}
              {anyOverDraw ? (
                <span className="text-destructive ml-2">
                  Over remaining slips
                </span>
              ) : null}
            </>
          ) : (
            <span className="text-white/35">Tap a prize to record</span>
          )}
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onOpenChange(false)}
          disabled={isPending}
        >
          Cancel
        </Button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={confirmDisabled}
          className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-[13px] font-medium text-white transition ${
            confirmDisabled
              ? "bg-brand-primary/30 cursor-not-allowed"
              : "bg-brand-primary hover:bg-brand-primary/90 shadow-[0_4px_12px_rgba(124,58,237,0.4)]"
          }`}
        >
          {isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Confirm draw
        </button>
      </div>
     </TierClassColorProvider>
    </DialogChrome>
  );
}
