"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { LootboxPrize } from "@/types/lootbox";

export type ReelPhase = "idle" | "spinning" | "won";

export interface ReelStripCard {
  readonly key: string;
  readonly prizeId: string;
}

export interface UseReelArgs {
  readonly prizes: readonly LootboxPrize[];
  readonly cardWidth: number;
  readonly cardGap: number;
  readonly spinMs: number;
  readonly winnerIdx?: number;
  readonly stripLength?: number;
}

export interface UseReelReturn {
  readonly phase: ReelPhase;
  readonly strip: readonly ReelStripCard[];
  readonly translate: number;
  readonly winnerPrizeId: string | null;
  readonly winnerIdx: number;
  readonly spinMs: number;
  readonly viewportRef: React.RefObject<HTMLDivElement | null>;
  readonly open: (winnerPrizeId: string) => void;
  readonly reset: () => void;
}

const DEFAULT_STRIP = 64;
const DEFAULT_WINNER_IDX = 56;
const SETTLE_BUFFER_MS = 200;
const MOUNT_DELAY_MS = 30;

// Shared by useReel (stride math) and HeroZone (Reel render size). Both MUST
// use the same values or the strip will land off-center from the pointer.
// Sized to the v2 design handoff: 6 cards visible, flex:1 in a 924px container with
// gap 12. (924 - 5*12) / 6 = 144 ≈ cardW. Reel container width = exactly N*cardW +
// (N-1)*gap so the cards span the viewport edge-to-edge with the centre pointer
// landing in the middle of the strip.
export const REEL_DIM_DESKTOP = { cardW: 144, gap: 12, height: 144 } as const;
export const REEL_DIM_MOBILE = { cardW: 86, gap: 6, height: 86 } as const;

// Mirrors the V4 prototype's useReel (refs/design_handoff_loot_crate/desktop.jsx).
// The strip is built once at open() with the winner at winnerIdx; translate goes
// from 0 to the computed target with a single CSS transition. No "rolling" phase,
// no mid-spin strip rebuilds — those introduce closure/race bugs.
export function useReel({
  prizes,
  cardWidth,
  cardGap,
  spinMs,
  winnerIdx = DEFAULT_WINNER_IDX,
  stripLength = DEFAULT_STRIP,
}: UseReelArgs): UseReelReturn {
  const stride = cardWidth + cardGap;
  const viewportRef = useRef<HTMLDivElement | null>(null);

  const [phase, setPhase] = useState<ReelPhase>("idle");
  const [strip, setStrip] = useState<readonly ReelStripCard[]>([]);
  const [translate, setTranslate] = useState(0);
  const [winnerPrizeId, setWinnerPrizeId] = useState<string | null>(null);

  const prizeIds = useMemo(() => prizes.map((p) => p.id), [prizes]);
  const prizeIdsRef = useRef(prizeIds);
  prizeIdsRef.current = prizeIds;

  const targetTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wonTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimers = useCallback(() => {
    if (targetTimerRef.current) {
      clearTimeout(targetTimerRef.current);
      targetTimerRef.current = null;
    }
    if (wonTimerRef.current) {
      clearTimeout(wonTimerRef.current);
      wonTimerRef.current = null;
    }
  }, []);

  const reset = useCallback(() => {
    clearTimers();
    setPhase("idle");
    setStrip([]);
    setTranslate(0);
    setWinnerPrizeId(null);
  }, [clearTimers]);

  const open = useCallback(
    (wid: string) => {
      clearTimers();
      const ids = prizeIdsRef.current;
      if (ids.length === 0) return;

      const seed = Date.now();
      const items: ReelStripCard[] = [];
      for (let i = 0; i < stripLength; i += 1) {
        if (i === winnerIdx) {
          items.push({ key: `w${i}-${seed}`, prizeId: wid });
          continue;
        }
        // Never repeat the winner within 3 cards of the marker.
        let id: string;
        let tries = 0;
        do {
          id = ids[Math.floor(Math.random() * ids.length)]!;
          tries += 1;
        } while (
          Math.abs(i - winnerIdx) < 3 &&
          id === wid &&
          ids.length > 1 &&
          tries < 8
        );
        items.push({ key: `s${i}-${seed}`, prizeId: id });
      }

      setStrip(items);
      setWinnerPrizeId(wid);
      setTranslate(0);
      setPhase("spinning");

      // Mount strip at translate=0 first, then commit target so the browser
      // sees a transitionable change and animates over spinMs.
      targetTimerRef.current = setTimeout(() => {
        // Use `||` not `??` — a hidden viewport reports offsetWidth=0, which
        // would otherwise pass through and wreck the centering math.
        const vw = viewportRef.current?.offsetWidth || 800;
        const jitter = (Math.random() - 0.5) * (cardWidth * 0.5);
        const target = -(winnerIdx * stride + cardWidth / 2 - vw / 2 + jitter);
        setTranslate(target);
      }, MOUNT_DELAY_MS);

      wonTimerRef.current = setTimeout(() => {
        setPhase("won");
      }, spinMs + SETTLE_BUFFER_MS);
    },
    [cardWidth, clearTimers, spinMs, stride, stripLength, winnerIdx]
  );

  useEffect(() => clearTimers, [clearTimers]);

  return {
    phase,
    strip,
    translate,
    winnerPrizeId,
    winnerIdx,
    spinMs,
    viewportRef,
    open,
    reset,
  };
}
