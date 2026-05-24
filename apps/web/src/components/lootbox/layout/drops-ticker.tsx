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
      <div
        className="inline-flex shrink-0 items-center gap-2 rounded-full px-2.5 py-1 font-mono text-[9.5px] uppercase tracking-[0.18em]"
        style={{
          color: "#bda1ff",
          backgroundColor: "rgba(139,92,246,0.10)",
          border: "1px solid rgba(139,92,246,0.25)",
        }}
      >
        <span
          className="inline-block h-1.5 w-1.5 rounded-full"
          style={{ backgroundColor: "#a87bff", boxShadow: "0 0 8px #a87bff" }}
        />
        Recent Wins
      </div>

      <div className="scrollbar-none flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
        {isLoading ? (
          <span className="text-[11px] text-muted-foreground">Loading…</span>
        ) : !drops || drops.length === 0 ? (
          <span className="text-[11px] text-muted-foreground">No Opens Yet</span>
        ) : (
          drops.map((drop) => <DropChip key={drop.id} drop={drop} now={now} />)
        )}
      </div>

    </div>
  );
}

function DropChip({ drop, now }: { readonly drop: RecentLootboxPlay; readonly now: Date }) {
  const color = resolveTierColor(drop.tierColor);
  return (
    <div
      className="flex shrink-0 items-center gap-2 rounded-md py-1 pl-1 pr-2.5"
      style={{
        backgroundColor: hexWithAlpha(color, 0.08),
        boxShadow: `inset 0 0 0 1px ${hexWithAlpha(color, 0.32)}`,
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
            sizes="48px"
            className="h-full w-full object-cover"
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
