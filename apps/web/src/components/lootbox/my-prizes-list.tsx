"use client";

import Image from "next/image";
import { Gift } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { hexWithAlpha, resolveTierColor } from "@/components/lootbox/tier-helpers";
import { formatTimeAgo } from "@/components/lootbox/tier-helpers";
import type { LootboxPlay } from "@/types/lootbox";

interface MyPrizesListProps {
  readonly prizes: readonly LootboxPlay[] | undefined;
  readonly isLoading: boolean;
}

/**
 * Player-facing "wins" view, styled to match the Prize pool grid: tier-grouped
 * sections with a 4-col card grid. Group keys come from the play's snapshot
 * (prizeTierName), not the live tier — so a play retains its original tier label
 * even if the tier was later renamed or hard-deleted.
 */
export function MyPrizesList({ prizes, isLoading }: MyPrizesListProps) {
  if (isLoading) {
    return (
      <div className="space-y-6">
        {Array.from({ length: 2 }).map((_, idx) => (
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

  if (!prizes || prizes.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        nothing but flies
      </p>
    );
  }

  // Group by tier name (snapshotted on the play row at spin time).
  const groups = new Map<string, LootboxPlay[]>();
  for (const p of prizes) {
    const key = p.prizeTierName || "Unranked";
    const arr = groups.get(key) ?? [];
    arr.push(p);
    groups.set(key, arr);
  }

  return (
    <div className="space-y-6">
      {Array.from(groups.entries()).map(([tierName, plays]) => (
        <TierWinsSection
          key={tierName}
          tierName={tierName}
          plays={plays}
        />
      ))}
    </div>
  );
}

function TierWinsSection({
  tierName,
  plays,
}: {
  readonly tierName: string;
  readonly plays: readonly LootboxPlay[];
}) {
  // No live tier color in the play snapshot — fall back to the default. Matches
  // PrizeGrid's tier-color treatment so the visual rhythm is consistent.
  const color = resolveTierColor(null);

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
            {tierName}
          </span>
        </div>
        <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
          {plays.length}
        </span>
      </div>

      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
        {plays.map((play) => (
          <WonPrizeCard key={play.id} play={play} color={color} />
        ))}
      </div>
    </div>
  );
}

function WonPrizeCard({
  play,
  color,
}: {
  readonly play: LootboxPlay;
  readonly color: string;
}) {
  const redeemed = play.status === "REDEEMED";
  return (
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
        {play.prizeImageUrl ? (
          <Image
            src={play.prizeImageUrl}
            alt={play.prizeName}
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
            {play.prizeName}
          </div>
          <RedemptionBadge redeemed={redeemed} />
        </div>
        <div className="mt-0.5 truncate font-mono text-[11px] text-muted-foreground">
          Won {formatTimeAgo(play.playedAt, new Date())}
        </div>
      </div>
    </div>
  );
}

function RedemptionBadge({ redeemed }: { readonly redeemed: boolean }) {
  return (
    <span
      className={
        redeemed
          ? "shrink-0 rounded-full bg-emerald-500/15 px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.1em] text-emerald-400"
          : "shrink-0 rounded-full bg-amber-500/15 px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.1em] text-amber-400"
      }
    >
      {redeemed ? "Redeemed" : "Pending"}
    </span>
  );
}
