"use client";

import { useMemo } from "react";
import { ReelCard } from "./reel-card";
import type { ReelPhase, ReelStripCard } from "./use-reel";
import type { LootboxPrize } from "@/types/lootbox";

interface ReelProps {
  readonly phase: ReelPhase;
  readonly strip: readonly ReelStripCard[];
  readonly translate: number;
  readonly prizes: readonly LootboxPrize[];
  readonly cardWidth: number;
  readonly cardGap: number;
  readonly height: number;
  readonly spinMs: number;
  readonly viewportRef: React.RefObject<HTMLDivElement | null>;
  readonly winnerIdx: number;
  readonly winnerPrizeId: string | null;
}

const REEL_EASING = "cubic-bezier(0.08, 0.82, 0.17, 1)";

export function Reel({
  phase,
  strip,
  translate,
  prizes,
  cardWidth,
  cardGap,
  height,
  spinMs,
  viewportRef,
  winnerIdx,
  winnerPrizeId,
}: ReelProps) {
  const byId = useMemo(() => {
    const m = new Map<string, LootboxPrize>();
    prizes.forEach((p) => m.set(p.id, p));
    return m;
  }, [prizes]);

  const announce =
    phase === "spinning"
      ? "Spinning…"
      : phase === "won"
        ? "You won."
        : "Ready to open.";

  return (
    <div
      ref={viewportRef}
      role="status"
      aria-live="polite"
      className="relative w-full overflow-hidden rounded-lg border border-border bg-background-deep"
      style={{ height }}
    >
      <span className="sr-only">{announce}</span>

      <div
        aria-hidden
        className="pointer-events-none absolute inset-y-0 left-1/2 z-30 flex -translate-x-1/2 flex-col items-center justify-between py-0.5"
      >
        <span
          className="text-[11px] leading-none text-brand-primary"
          style={{ filter: "drop-shadow(0 0 4px var(--brand-primary))" }}
        >
          ▼
        </span>
        <span
          className="w-px flex-1 bg-brand-primary"
          style={{ boxShadow: "0 0 6px var(--brand-primary)" }}
        />
        <span
          className="text-[11px] leading-none text-brand-primary"
          style={{ filter: "drop-shadow(0 0 4px var(--brand-primary))" }}
        >
          ▲
        </span>
      </div>

      <div
        className="flex h-full items-center will-change-transform"
        style={{
          gap: cardGap,
          transform: `translate3d(${translate}px, 0, 0)`,
          transition:
            phase === "spinning" ? `transform ${spinMs}ms ${REEL_EASING}` : "none",
        }}
      >
        {strip.length === 0
          ? Array.from({ length: 32 }).map((_, idx) => (
              <div
                key={`skeleton-${idx}`}
                className="shrink-0 rounded-md border border-border/40 bg-card/40"
                style={{ width: cardWidth, height: height - 16 }}
              />
            ))
          : strip.map((card, idx) => (
              <ReelCard
                key={card.key}
                prize={byId.get(card.prizeId)}
                width={cardWidth}
                height={height - 16}
                highlight={
                  phase === "won" &&
                  idx === winnerIdx &&
                  card.prizeId === winnerPrizeId
                }
              />
            ))}
      </div>
    </div>
  );
}
