"use client";

import Image from "next/image";
import { Gift } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import {
  fmtPct,
  hexWithAlpha,
  resolveTierColor,
} from "@/components/lootbox/tier-helpers";
import type { LootboxPrize, LootboxTier } from "@/types/lootbox";

interface PrizeGridProps {
  readonly tiers: readonly LootboxTier[] | undefined;
  readonly isLoading: boolean;
}

export function PrizeGrid({ tiers, isLoading }: PrizeGridProps) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-5">
        <Skeleton className="h-4 w-24" />
        <div className="mt-4 grid grid-cols-2 gap-1.5 sm:grid-cols-3 md:grid-cols-4">
          {Array.from({ length: 8 }).map((_, idx) => (
            <Skeleton key={idx} className="h-14 w-full" />
          ))}
        </div>
      </div>
    );
  }

  const activeTiers = (tiers ?? []).filter((t) => t.active);
  const totalPrizes = activeTiers.reduce((acc, t) => acc + t.prizes.length, 0);

  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-[13px] font-medium">Prize pool</h3>
        <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-muted-foreground">
          {totalPrizes} prizes · {activeTiers.length} tier{activeTiers.length === 1 ? "" : "s"}
        </span>
      </div>

      <div className="mt-3 flex flex-col gap-3">
        {activeTiers.map((tier) => (
          <TierGroup key={tier.id} tier={tier} />
        ))}
        {activeTiers.length === 0 ? (
          <p className="text-sm text-muted-foreground">No active tiers configured.</p>
        ) : null}
      </div>
    </div>
  );
}

function TierGroup({ tier }: { readonly tier: LootboxTier }) {
  const color = resolveTierColor(tier.displayColor);
  return (
    <div>
      <div className="flex items-center gap-2 pb-1.5">
        <span
          className="inline-block h-2 w-2 rounded-full"
          style={{ backgroundColor: color }}
          aria-hidden
        />
        <span
          className="font-mono text-[10px] uppercase tracking-[0.16em]"
          style={{ color }}
        >
          {tier.name}
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-muted-foreground">
          {fmtPct(tier.probabilityPct)}
        </span>
        <span className="ml-auto font-mono text-[10px] uppercase tracking-[0.12em] text-muted-foreground">
          {tier.prizes.length}
        </span>
      </div>
      {tier.prizes.length === 0 ? (
        <p className="text-xs text-muted-foreground">No prizes in this tier yet.</p>
      ) : (
        <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3 md:grid-cols-4">
          {tier.prizes.map((prize) => (
            <PrizeChip key={prize.id} prize={prize} color={color} />
          ))}
        </div>
      )}
    </div>
  );
}

function PrizeChip({ prize, color }: { readonly prize: LootboxPrize; readonly color: string }) {
  return (
    <div
      className="flex items-center gap-2 rounded-md p-2"
      style={{
        backgroundColor: hexWithAlpha(color, 0.08),
        boxShadow: `inset 0 0 0 1px ${hexWithAlpha(color, 0.32)}`,
      }}
    >
      <div
        className="flex h-[26px] w-[26px] shrink-0 items-center justify-center overflow-hidden rounded"
        style={{ backgroundColor: hexWithAlpha(color, 0.2) }}
      >
        {prize.imageUrl ? (
          <Image
            src={prize.imageUrl}
            alt={prize.name}
            width={26}
            height={26}
            className="h-full w-full object-cover"
            unoptimized
          />
        ) : (
          <Gift className="h-3.5 w-3.5" style={{ color }} />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-[10.5px] font-medium leading-tight text-foreground">
          {prize.name}
        </div>
        {prize.description ? (
          <div className="truncate font-mono text-[9px] text-muted-foreground">
            {prize.description}
          </div>
        ) : null}
      </div>
    </div>
  );
}
