"use client";

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { fmtPct, resolveTierColor } from "@/components/lootbox/tier-helpers";
import type { LootboxTier } from "@/types/lootbox";

interface DropRatesSheetProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly tiers: readonly LootboxTier[] | undefined;
}

export function DropRatesSheet({ open, onOpenChange, tiers }: DropRatesSheetProps) {
  const activeTiers = (tiers ?? []).filter((t) => t.active);
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Drop rates</SheetTitle>
          <SheetDescription>
            Probabilities are evaluated server-side at every open.
          </SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-3 px-4 pb-4">
          {activeTiers.length === 0 ? (
            <p className="text-sm text-muted-foreground">No active tiers configured.</p>
          ) : (
            activeTiers.map((tier) => {
              const color = resolveTierColor(tier.displayColor);
              const perPrize =
                tier.prizes.length > 0
                  ? tier.probabilityPct / tier.prizes.length
                  : tier.probabilityPct;
              return (
                <div key={tier.id} className="rounded-md border border-border p-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span
                        className="inline-block h-2 w-2 rounded-full"
                        style={{ backgroundColor: color }}
                        aria-hidden
                      />
                      <span
                        className="font-mono text-[11px] uppercase tracking-[0.16em]"
                        style={{ color }}
                      >
                        {tier.name}
                      </span>
                    </div>
                    <span className="font-mono text-[11px] tabular-nums text-foreground">
                      {fmtPct(tier.probabilityPct)}
                    </span>
                  </div>
                  <div className="mt-2 space-y-1">
                    {tier.prizes.map((prize) => (
                      <div
                        key={prize.id}
                        className="flex items-center justify-between gap-2 text-xs"
                      >
                        <span className="truncate text-foreground">{prize.name}</span>
                        <span className="font-mono tabular-nums text-muted-foreground">
                          {fmtPct(perPrize)}
                        </span>
                      </div>
                    ))}
                    {tier.prizes.length === 0 ? (
                      <p className="text-xs text-muted-foreground">No prizes in this tier.</p>
                    ) : null}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
