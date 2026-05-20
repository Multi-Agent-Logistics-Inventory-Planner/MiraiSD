"use client";

import { Coins } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CrateIllustration } from "@/components/lootbox/crate-illustration";
import { Reel } from "@/components/lootbox/reel/reel";
import { WinFlash } from "@/components/lootbox/reel/win-flash";
import {
  REEL_DIM_DESKTOP,
  REEL_DIM_MOBILE,
  type ReelPhase,
  type ReelStripCard,
} from "@/components/lootbox/reel/use-reel";
import { resolveTierColor } from "@/components/lootbox/tier-helpers";
import type { LootboxPlay, LootboxPrize } from "@/types/lootbox";

interface HeroZoneProps {
  readonly prizes: readonly LootboxPrize[];
  readonly phase: ReelPhase;
  readonly strip: readonly ReelStripCard[];
  readonly translate: number;
  readonly spinMs: number;
  readonly viewportRef: React.RefObject<HTMLDivElement | null>;
  readonly winnerIdx: number;
  readonly winnerPrizeId: string | null;
  readonly isDesktop: boolean;
  readonly lastWin: LootboxPlay | null;
  readonly balance: number;
  readonly openedToday: number;
  readonly isOpening: boolean;
  readonly onOpen: () => void;
  readonly onKeep: () => void;
  readonly onOpenAgain: () => void;
}

export function HeroZone(props: HeroZoneProps) {
  const {
    prizes,
    phase,
    strip,
    translate,
    spinMs,
    viewportRef,
    winnerIdx,
    winnerPrizeId,
    isDesktop,
    lastWin,
    balance,
    openedToday,
    isOpening,
    onOpen,
    onKeep,
    onOpenAgain,
  } = props;

  const dim = isDesktop ? REEL_DIM_DESKTOP : REEL_DIM_MOBILE;

  const canOpen = balance >= 1 && phase === "idle" && !isOpening;
  const buttonLabel =
    balance < 1
      ? "Out of coins"
      : isOpening
        ? "Opening…"
        : phase === "spinning"
          ? "Unboxing…"
          : phase === "won"
            ? "You won!"
            : "Open Lootbox · 1 coin";

  const winTierColor = resolveTierColor(
    prizes.find((p) => p.id === lastWin?.prizeId)?.tierColor ?? null
  );
  const showWinFlash = phase === "won" && lastWin;

  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-[260px_1fr]">
        <div className="flex flex-col items-center gap-3 rounded-lg border border-border bg-background-deep p-4">
          <div className="text-center">
            <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-muted-foreground">
              Crate · Vol. I
            </div>
            <div className="text-[15px] font-medium text-foreground">Mirai Mystery Crate</div>
            <div className="font-mono text-[10px] uppercase tracking-[0.12em] text-muted-foreground">
              {prizes.length} prize{prizes.length === 1 ? "" : "s"}
            </div>
          </div>
          <div className="hidden sm:block">
            <CrateIllustration size={180} tint="#a78bfa" />
          </div>
          <div className="sm:hidden">
            <CrateIllustration size={140} tint="#a78bfa" />
          </div>
          <div className="grid w-full grid-cols-2 gap-2">
            <Stat label="Cost" value="1 coin" />
            <Stat
              label="Today"
              value={<span className="tabular-nums">{openedToday}</span>}
              accent
            />
          </div>
        </div>

        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex items-end justify-between gap-3">
            <div>
              <div className="text-[15px] font-medium">
                {phase === "won"
                  ? "You won!"
                  : phase === "spinning"
                    ? "Unboxing…"
                    : "Ready to unbox"}
              </div>
              <div className="text-[11px] text-muted-foreground">
                1 coin per open · weighted server-side
              </div>
            </div>
            <div className="flex items-center gap-3 text-right">
              <Stat
                label="Coins"
                value={<span className="tabular-nums">{balance}</span>}
                compact
              />
            </div>
          </div>

          <Reel
            phase={phase}
            strip={strip}
            translate={translate}
            prizes={prizes}
            cardWidth={dim.cardW}
            cardGap={dim.gap}
            height={dim.height}
            spinMs={spinMs}
            viewportRef={viewportRef}
            winnerIdx={winnerIdx}
            winnerPrizeId={winnerPrizeId}
          />

          <Button
            size="lg"
            className="w-full bg-brand-primary text-white hover:bg-brand-primary-hover"
            disabled={!canOpen}
            onClick={onOpen}
          >
            <Coins className="h-4 w-4" />
            {buttonLabel}
          </Button>

          {showWinFlash ? (
            <WinFlash
              play={lastWin}
              tierColor={winTierColor}
              canOpenAgain={balance >= 1}
              onKeep={onKeep}
              onOpenAgain={onOpenAgain}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
}

function Stat({
  label,
  value,
  accent,
  compact,
}: {
  readonly label: string;
  readonly value: React.ReactNode;
  readonly accent?: boolean;
  readonly compact?: boolean;
}) {
  return (
    <div
      className={
        compact
          ? "flex items-baseline gap-1"
          : "flex flex-col rounded-md border border-border bg-card px-2 py-1.5"
      }
    >
      <span className="font-mono text-[9px] uppercase tracking-[0.16em] text-muted-foreground">
        {label}
      </span>
      <span
        className={
          accent
            ? "text-[13px] font-medium text-brand-primary-light"
            : "text-[13px] font-medium text-foreground"
        }
      >
        {value}
      </span>
    </div>
  );
}
