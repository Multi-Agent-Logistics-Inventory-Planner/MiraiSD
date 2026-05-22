"use client";

import { Coins } from "lucide-react";
import { CrateIllustration } from "@/components/lootbox/crate-illustration";
import { CrateCarousel } from "@/components/lootbox/layout/crate-carousel";
import { Reel } from "@/components/lootbox/reel/reel";
import { WinFlash } from "@/components/lootbox/reel/win-flash";
import {
  REEL_DIM_DESKTOP,
  REEL_DIM_MOBILE,
  type ReelPhase,
  type ReelStripCard,
} from "@/components/lootbox/reel/use-reel";
import { resolveTierColor } from "@/components/lootbox/tier-helpers";
import type { Lootbox, LootboxPlay, LootboxPrize } from "@/types/lootbox";

interface HeroZoneProps {
  readonly crate: Lootbox | null;
  readonly crates: readonly Lootbox[];
  readonly onSelectCrate: (id: string) => void;
  readonly prizes: readonly LootboxPrize[];
  readonly tierCount: number;
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
  readonly isOpening: boolean;
  readonly onOpen: () => void;
  readonly onKeep: () => void;
  readonly onOpenAgain: () => void;
}

export function HeroZone(props: HeroZoneProps) {
  const {
    crate,
    crates,
    onSelectCrate,
    prizes,
    tierCount,
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
    isOpening,
    onOpen,
    onKeep,
    onOpenAgain,
  } = props;

  const dim = isDesktop ? REEL_DIM_DESKTOP : REEL_DIM_MOBILE;
  const cost = crate?.cost ?? 1;
  const crateName = crate?.name ?? "Mirai Mystery Crate";
  const crateDescription = crate?.description ?? null;
  const showCarousel = crates.length > 1;
  const isEmpty = !!crate && prizes.length === 0;

  const canOpen =
    !!crate && !isEmpty && balance >= cost && phase === "idle" && !isOpening;

  const buttonLabel = !crate
    ? "Loading…"
    : isEmpty
      ? "Coming soon"
      : balance < cost
        ? "Out of coins"
        : isOpening
          ? "Opening…"
          : phase === "spinning"
            ? "Unboxing…"
            : phase === "won"
              ? "You won!"
              : `Open · ${cost} coin${cost === 1 ? "" : "s"}`;

  const useGradientButton = !isEmpty && (canOpen || isOpening || phase !== "idle");

  const winTierColor = resolveTierColor(
    prizes.find((p) => p.id === lastWin?.prizeId)?.tierColor ?? null
  );
  const showWinFlash = phase === "won" && lastWin;

  return (
    <div
      className="relative overflow-hidden rounded-2xl border border-border px-6 pb-8 pt-10 sm:px-10"
      style={{
        background:
          "radial-gradient(ellipse at 50% 30%, rgba(139,92,246,0.10), transparent 60%), var(--card)",
      }}
    >
      <div className="pointer-events-none absolute left-[28px] top-[22px] hidden font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground sm:block">
        {crate?.endsAt ? "Limited time" : "Crate"}
      </div>
      <div className="pointer-events-none absolute right-[28px] top-[22px] hidden font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground sm:block">
        <span className="tabular-nums">{prizes.length}</span> prize
        {prizes.length === 1 ? "" : "s"} · <span className="tabular-nums">{tierCount}</span> tier
        {tierCount === 1 ? "" : "s"}
      </div>

      <div className="mt-2 flex flex-col items-center gap-3.5">
        {showCarousel ? (
          <CrateCarousel
            crates={crates}
            selectedId={crate?.id ?? null}
            onSelect={onSelectCrate}
          />
        ) : (
          <>
            <h1 className="m-0 text-[28px] font-semibold tracking-[-0.4px] text-foreground">
              {crateName}
            </h1>
            <div className="inline-flex items-center gap-2.5 font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
              <span>
                {cost === 0 ? "Free spin" : `${cost} coin${cost === 1 ? "" : "s"} per open`}
              </span>
              <span className="opacity-60">·</span>
              <span>{crateDescription ?? "weighted server-side"}</span>
            </div>
          </>
        )}
        <CrateIllustration size={140} tint="#a78bfa" />
      </div>

      <div className="mx-auto mt-8 max-w-[920px]">
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
      </div>

      <div className="mx-auto mt-7 max-w-[520px]">
        <button
          type="button"
          disabled={!canOpen}
          onClick={onOpen}
          className="cursor-pointer inline-flex w-full items-center justify-center gap-2.5 rounded-xl px-5 py-3.5 text-[15px] font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:text-muted-foreground"
          style={
            useGradientButton
              ? {
                  background: "linear-gradient(180deg, #9d6cff 0%, #7c3aed 100%)",
                  border: "1px solid rgba(255,255,255,0.12)",
                  boxShadow:
                    "0 8px 24px -8px rgba(139,92,246,0.55), inset 0 1px 0 rgba(255,255,255,0.18)",
                }
              : {
                  background: "var(--background-deep)",
                  border: "1px solid var(--border)",
                }
          }
        >
          <Coins className="h-4 w-4" />
          {buttonLabel}
        </button>
      </div>

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
  );
}
