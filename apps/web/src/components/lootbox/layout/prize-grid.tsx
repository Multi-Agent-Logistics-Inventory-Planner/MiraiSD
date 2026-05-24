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
      <div className="space-y-6">
        {Array.from({ length: 3 }).map((_, idx) => (
          <div key={idx} className="space-y-2">
            <Skeleton className="h-3 w-32" />
            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
              <Skeleton className="h-[60px] w-full rounded-[10px]" />
              <Skeleton className="h-[60px] w-full rounded-[10px]" />
              <Skeleton className="h-[60px] w-full rounded-[10px]" />
              <Skeleton className="h-[60px] w-full rounded-[10px]" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  // Show tiers that are active OR that have any sold-out prize (so depleted prizes
  // remain visible to players even after their tier auto-deactivated).
  const visibleTiers = (tiers ?? []).filter(
    (t) => t.active || t.prizes.some((p) => p.quantity === 0)
  );

  if (visibleTiers.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">No active tiers configured.</p>
    );
  }

  return (
    <div className="space-y-6">
      {visibleTiers.map((tier) => (
        <TierSection key={tier.id} tier={tier} />
      ))}
    </div>
  );
}

function PrizeStockBadge({ quantity }: { readonly quantity: number | null }) {
  if (quantity === null) return null;
  if (quantity === 0) {
    return (
      <span className="shrink-0 rounded-full bg-rose-500/15 px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.1em] text-rose-400">
        Won out
      </span>
    );
  }
  return (
    <span className="shrink-0 rounded-full bg-amber-500/15 px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.1em] text-amber-400">
      {quantity} left
    </span>
  );
}

function TierSection({ tier }: { readonly tier: LootboxTier }) {
  const color = resolveTierColor(tier.displayColor);

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <div className="inline-flex items-center gap-2">
          <span
            className="inline-block h-[7px] w-[7px] rounded-full"
            style={{ backgroundColor: color }}
            aria-hidden
          />
          <span
            className="font-mono text-[11px] uppercase tracking-[0.16em]"
            style={{ color }}
          >
            {tier.name}
          </span>
          <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
            {fmtPct(tier.probabilityPct)}
          </span>
        </div>
        <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
          {tier.prizes.length}
        </span>
      </div>

      {tier.prizes.length === 0 ? (
        <div className="rounded-[10px] border border-dashed border-border p-3 font-mono text-[11px] text-muted-foreground">
          No prizes yet
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
          {tier.prizes.map((prize) => (
            <PrizeCard key={prize.id} prize={prize} color={color} />
          ))}
        </div>
      )}
    </div>
  );
}

function PrizeCard({
  prize,
  color,
}: {
  readonly prize: LootboxPrize;
  readonly color: string;
}) {
  // Depleted prizes get a faded shell. The card itself stays interactive (parent
  // may wire a click handler later) — for now it's purely informational.
  const soldOut = prize.quantity === 0;
  return (
    <div
      className="flex items-center gap-3 rounded-[10px] p-3 transition-colors"
      style={{
        backgroundColor: hexWithAlpha(color, soldOut ? 0.03 : 0.07),
        boxShadow: `inset 0 0 0 1px ${hexWithAlpha(color, soldOut ? 0.18 : 0.32)}`,
        opacity: soldOut ? 0.65 : 1,
      }}
    >
      <div
        className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-md"
        style={{
          backgroundColor: hexWithAlpha(color, 0.16),
          boxShadow: `inset 0 0 0 1px ${hexWithAlpha(color, 0.32)}`,
          color,
        }}
      >
        {prize.imageUrl ? (
          <Image
            src={prize.imageUrl}
            alt={prize.name}
            width={32}
            height={32}
            sizes="48px"
            className="h-full w-full object-cover"
          />
        ) : (
          <Gift className="h-4 w-4" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <div className="truncate text-[13px] font-medium leading-tight text-foreground">
            {prize.name}
          </div>
          <PrizeStockBadge quantity={prize.quantity} />
        </div>
        {prize.description ? (
          <div className="mt-0.5 truncate font-mono text-[11px] text-muted-foreground">
            {prize.description}
          </div>
        ) : null}
      </div>
    </div>
  );
}
