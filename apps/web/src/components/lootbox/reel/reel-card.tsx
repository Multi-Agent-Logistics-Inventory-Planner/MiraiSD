"use client";

import Image from "next/image";
import { Gift } from "lucide-react";
import { hexWithAlpha, resolveTierColor } from "@/components/lootbox/tier-helpers";
import type { LootboxPrize } from "@/types/lootbox";

interface ReelCardProps {
  readonly prize: LootboxPrize | undefined;
  readonly width: number;
  readonly height: number;
  readonly highlight?: boolean;
}

export function ReelCard({ prize, width, height, highlight }: ReelCardProps) {
  const color = resolveTierColor(prize?.tierColor);
  const imageUrl = prize?.imageUrl ?? null;
  return (
    <div
      className="flex shrink-0 flex-col items-center justify-between rounded-md p-2 transition-shadow"
      style={{
        width,
        height,
        backgroundColor: hexWithAlpha(color, highlight ? 0.22 : 0.1),
        border: `${highlight ? 1 : 0.5}px solid ${hexWithAlpha(color, highlight ? 0.95 : 0.38)}`,
        boxShadow: highlight
          ? `0 0 18px ${hexWithAlpha(color, 0.7)}, inset 0 0 0 1px ${hexWithAlpha(color, 0.6)}`
          : `inset 0 0 0 1px ${hexWithAlpha(color, 0.18)}`,
      }}
    >
      <div
        className="flex h-12 w-12 items-center justify-center rounded-md overflow-hidden"
        style={{ backgroundColor: hexWithAlpha(color, 0.2) }}
      >
        {imageUrl ? (
          <Image
            src={imageUrl}
            alt={prize?.name ?? "Prize"}
            width={48}
            height={48}
            sizes="96px"
            className="h-full w-full object-cover"
          />
        ) : (
          <Gift className="h-6 w-6" style={{ color }} />
        )}
      </div>
      <div className="w-full text-center text-[10.5px] font-medium leading-tight text-foreground line-clamp-2">
        {prize?.name ?? "—"}
      </div>
      <div
        className="font-mono text-[9px] uppercase tracking-[0.12em]"
        style={{ color }}
      >
        {prize?.tierName ?? ""}
      </div>
    </div>
  );
}
