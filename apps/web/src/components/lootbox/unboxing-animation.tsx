"use client";

import { Loader2 } from "lucide-react";
import { PrizeCard } from "./prize-card";
import type { LootboxPlay } from "@/types/lootbox";

interface UnboxingAnimationProps {
  result: LootboxPlay | null;
  pending: boolean;
}

/**
 * v1 stub: renders a spinner while a play is in-flight, then the prize card.
 * Swap with a richer reveal (reel / card flip / etc.) when the reference designs land.
 */
export function UnboxingAnimation({ result, pending }: UnboxingAnimationProps) {
  if (pending) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed bg-muted/20 p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        <p className="text-sm text-muted-foreground">Opening lootbox…</p>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="flex items-center justify-center rounded-lg border border-dashed bg-muted/20 p-12 text-sm text-muted-foreground">
        Open a lootbox to see your prize here.
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <p className="text-sm text-muted-foreground">You won:</p>
      <PrizeCard
        name={result.prizeName}
        tierName={result.prizeTierName}
        description={result.prizeDescription}
        imageUrl={result.prizeImageUrl}
        className="max-w-xs"
      />
    </div>
  );
}
