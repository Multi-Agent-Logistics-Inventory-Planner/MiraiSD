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
    <div className="relative w-full">
      {/* Center pointers — two CSS triangles flanking the strip, no connecting line.
          Sit outside the overflow-hidden viewport so they never clip and don't
          introduce vertical scrolling. Matches refs/design_handoff_lootbox_v2/
          reference/option-a.jsx:95-109. */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 z-30 -translate-x-1/2"
        style={{
          top: -8,
          width: 0,
          height: 0,
          borderLeft: "6px solid transparent",
          borderRight: "6px solid transparent",
          borderTop: "8px solid var(--brand-primary)",
        }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 z-30 -translate-x-1/2"
        style={{
          bottom: -8,
          width: 0,
          height: 0,
          borderLeft: "6px solid transparent",
          borderRight: "6px solid transparent",
          borderBottom: "8px solid var(--brand-primary)",
        }}
      />
      {/* Glowing vertical line connecting the two pointer triangles. Sits on the
          wrapper (not the viewport) so it can render full-height without being
          clipped by the strip's overflow-hidden, and renders above the cards. */}
      <span
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-0 bottom-0 z-30 w-px -translate-x-1/2 bg-brand-primary"
        style={{ boxShadow: "0 0 6px var(--brand-primary)" }}
      />

    <div
      ref={viewportRef}
      role="status"
      aria-live="polite"
      className="relative w-full overflow-hidden"
      style={{ height }}
    >
      <span className="sr-only">{announce}</span>

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
    </div>
  );
}
