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
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, idx) => (
          <div key={idx} className="flex flex-col gap-2">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-20 w-full rounded-[10px]" />
          </div>
        ))}
      </div>
    );
  }

  const activeTiers = (tiers ?? []).filter((t) => t.active);

  if (activeTiers.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">No active tiers configured.</p>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {activeTiers.map((tier) => (
        <TierRow key={tier.id} tier={tier} />
      ))}
    </div>
  );
}

function TierRow({ tier }: { readonly tier: LootboxTier }) {
  const color = resolveTierColor(tier.displayColor);
  const headline: LootboxPrize | undefined = tier.prizes[0];
  const extraCount = Math.max(tier.prizes.length - 1, 0);

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

      <div
        className="flex items-center gap-3 rounded-[10px] p-3"
        style={{
          backgroundColor: hexWithAlpha(color, 0.07),
          boxShadow: `inset 0 0 0 1px ${hexWithAlpha(color, 0.32)}`,
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
          {headline?.imageUrl ? (
            <Image
              src={headline.imageUrl}
              alt={headline.name}
              width={32}
              height={32}
              className="h-full w-full object-cover"
              unoptimized
            />
          ) : (
            <Gift className="h-4 w-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          {headline ? (
            <>
              <div className="truncate text-[14px] font-medium leading-tight text-foreground">
                {headline.name}
              </div>
              <div className="truncate font-mono text-[11px] text-muted-foreground">
                {headline.description ?? "—"}
                {extraCount > 0 ? (
                  <span className="ml-1 text-muted-foreground/80">· +{extraCount} more</span>
                ) : null}
              </div>
            </>
          ) : (
            <div className="font-mono text-[11px] text-muted-foreground">No prizes yet</div>
          )}
        </div>
      </div>
    </div>
  );
}
