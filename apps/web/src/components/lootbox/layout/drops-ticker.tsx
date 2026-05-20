"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import { Gift } from "lucide-react";
import {
  formatTimeAgo,
  hexWithAlpha,
  resolveTierColor,
} from "@/components/lootbox/tier-helpers";
import type { RecentLootboxPlay } from "@/types/lootbox";

interface DropsTickerProps {
  readonly drops: readonly RecentLootboxPlay[] | undefined;
  readonly isLoading: boolean;
}

export function DropsTicker({ drops, isLoading }: DropsTickerProps) {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-2">
      <div className="flex shrink-0 items-center gap-1.5 font-mono text-[9.5px] uppercase tracking-[0.18em] text-brand-primary">
        <span
          className="inline-block h-1.5 w-1.5 rounded-full bg-brand-primary"
          style={{ boxShadow: "0 0 6px currentColor" }}
        />
        Live · team drops
      </div>

      <div className="scrollbar-none flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
        {isLoading ? (
          <span className="text-[11px] text-muted-foreground">Loading…</span>
        ) : !drops || drops.length === 0 ? (
          <span className="text-[11px] text-muted-foreground">No drops yet — be the first.</span>
        ) : (
          drops.map((drop) => <DropChip key={drop.id} drop={drop} now={now} />)
        )}
      </div>

      <div className="shrink-0 font-mono text-[9.5px] uppercase tracking-[0.18em] text-muted-foreground">
        Last {drops?.length ?? 0} drops
      </div>
    </div>
  );
}

function DropChip({ drop, now }: { readonly drop: RecentLootboxPlay; readonly now: Date }) {
  const color = resolveTierColor(drop.tierColor);
  return (
    <div
      className="flex shrink-0 items-center gap-2 rounded-full py-1 pl-1 pr-2.5"
      style={{
        backgroundColor: hexWithAlpha(color, 0.1),
        border: `0.5px solid ${hexWithAlpha(color, 0.38)}`,
      }}
    >
      <div
        className="flex h-[22px] w-[22px] items-center justify-center overflow-hidden rounded"
        style={{ backgroundColor: hexWithAlpha(color, 0.2) }}
      >
        {drop.prizeImageUrl ? (
          <Image
            src={drop.prizeImageUrl}
            alt={drop.prizeName}
            width={22}
            height={22}
            className="h-full w-full object-cover"
            unoptimized
          />
        ) : (
          <Gift className="h-3 w-3" style={{ color }} />
        )}
      </div>
      <div className="flex min-w-0 flex-col">
        <span className="truncate text-[10.5px] font-medium leading-tight text-foreground">
          {drop.prizeName}
        </span>
        <span className="truncate font-mono text-[9px] uppercase tracking-[0.08em] text-muted-foreground">
          {drop.userDisplay} · {formatTimeAgo(drop.playedAt, now)}
        </span>
      </div>
    </div>
  );
}
