"use client";

import Image from "next/image";
import { Gift } from "lucide-react";
import { Button } from "@/components/ui/button";
import { hexWithAlpha, resolveTierColor } from "@/components/lootbox/tier-helpers";
import type { LootboxPlay } from "@/types/lootbox";

interface WinFlashProps {
  readonly play: LootboxPlay | null;
  readonly tierColor: string | null | undefined;
  readonly canOpenAgain: boolean;
  readonly onKeep: () => void;
  readonly onOpenAgain: () => void;
}

export function WinFlash({
  play,
  tierColor,
  canOpenAgain,
  onKeep,
  onOpenAgain,
}: WinFlashProps) {
  if (!play) return null;
  const color = resolveTierColor(tierColor);
  const imageUrl = play.prizeImageUrl;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="You won"
      className="fixed inset-0 z-50 flex items-end justify-center bg-background/60 backdrop-blur-sm sm:items-center"
    >
      <div
        className="flex w-full max-w-md flex-col items-center gap-3 rounded-t-xl p-5 text-center sm:rounded-xl"
        style={{
          border: `1px solid ${hexWithAlpha(color, 0.45)}`,
          boxShadow: `0 0 32px ${hexWithAlpha(color, 0.35)}`,
          backgroundColor: `var(--card)`,
        }}
      >
        <span className="block h-1 w-10 rounded-full bg-muted-foreground/40 sm:hidden" aria-hidden />
        <div
          className="font-mono text-[10px] uppercase tracking-[0.18em]"
          style={{ color }}
        >
          {play.prizeTierName}
        </div>
        <div
          className="flex h-24 w-24 items-center justify-center overflow-hidden rounded-lg"
          style={{ backgroundColor: hexWithAlpha(color, 0.2) }}
        >
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt={play.prizeName}
              width={96}
              height={96}
              className="h-full w-full object-cover"
              unoptimized
            />
          ) : (
            <Gift className="h-12 w-12" style={{ color }} />
          )}
        </div>
        <div className="space-y-1">
          <div className="text-sm font-medium">You won!</div>
          <div className="text-lg font-semibold leading-tight">{play.prizeName}</div>
          {play.prizeDescription ? (
            <div className="text-xs text-muted-foreground">{play.prizeDescription}</div>
          ) : null}
        </div>
        <div className="mt-2 flex w-full items-center gap-2">
          <Button variant="outline" size="sm" className="flex-1" onClick={onKeep}>
            Keep it
          </Button>
          {canOpenAgain ? (
            <Button
              size="sm"
              className="flex-1 bg-brand-primary text-white hover:bg-brand-primary-hover"
              onClick={onOpenAgain}
            >
              Open another
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
