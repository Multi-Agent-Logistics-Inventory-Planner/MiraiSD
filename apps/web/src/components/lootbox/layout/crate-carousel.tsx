"use client";

import { useEffect, useRef } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Lootbox } from "@/types/lootbox";

interface CrateCarouselProps {
  readonly crates: readonly Lootbox[];
  readonly selectedId: string | null;
  readonly onSelect: (id: string) => void;
}

const SWIPE_THRESHOLD_PX = 40;

/**
 * Ceremonial crate selector: left/right arrows flank a center stack showing the
 * active crate name and dot indicators. Replaces the static crate name + caption
 * block inside the hero card when there is more than one crate available.
 */
export function CrateCarousel({ crates, selectedId, onSelect }: CrateCarouselProps) {
  const idx = crates.findIndex((c) => c.id === selectedId);
  const safeIdx = idx === -1 ? 0 : idx;
  const total = crates.length;
  const canPrev = safeIdx > 0;
  const canNext = safeIdx < total - 1;

  const goto = (next: number) => {
    if (next < 0 || next >= total) return;
    const target = crates[next];
    if (target && target.id !== selectedId) onSelect(target.id);
  };

  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      const target = document.activeElement;
      const isEditable =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        target instanceof HTMLSelectElement ||
        (target instanceof HTMLElement && target.isContentEditable);
      if (isEditable) return;
      if (e.key === "ArrowLeft") {
        if (canPrev) {
          e.preventDefault();
          goto(safeIdx - 1);
        }
      } else if (e.key === "ArrowRight") {
        if (canNext) {
          e.preventDefault();
          goto(safeIdx + 1);
        }
      }
    }
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
    // goto / canPrev / canNext are derived from safeIdx + crates each render; we only
    // want the listener to re-bind when the active index or list length actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [safeIdx, total]);

  const pointerStartX = useRef<number | null>(null);

  const onPointerDown = (e: React.PointerEvent) => {
    pointerStartX.current = e.clientX;
  };
  const onPointerUp = (e: React.PointerEvent) => {
    const start = pointerStartX.current;
    pointerStartX.current = null;
    if (start === null) return;
    const dx = e.clientX - start;
    if (Math.abs(dx) < SWIPE_THRESHOLD_PX) return;
    if (dx > 0 && canPrev) goto(safeIdx - 1);
    else if (dx < 0 && canNext) goto(safeIdx + 1);
  };

  const active = crates[safeIdx];
  if (!active) return null;

  return (
    <div
      className="flex w-full items-center justify-between gap-3 px-2 touch-pan-y sm:justify-center sm:gap-[18px] sm:px-0"
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      onPointerCancel={() => (pointerStartX.current = null)}
    >
      <CarouselArrow
        direction="prev"
        disabled={!canPrev}
        onClick={() => goto(safeIdx - 1)}
      />

      <div className="flex min-w-0 max-w-[360px] flex-1 flex-col items-center gap-3 sm:min-w-[280px] sm:max-w-[480px] sm:flex-initial">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Crate <span className="tabular-nums">{safeIdx + 1}</span> of{" "}
          <span className="tabular-nums">{total}</span>
        </span>
        <span
          key={active.id}
          className="block max-w-[360px] truncate text-[22px] font-semibold tracking-[-0.3px] text-foreground motion-safe:animate-in motion-safe:fade-in-0 motion-safe:duration-200"
          title={active.name}
        >
          {active.name}
        </span>
        <div className="flex items-center gap-1.5">
          {crates.map((c, i) => (
            <span
              key={c.id}
              aria-hidden="true"
              className={cn(
                "h-1.5 rounded-full transition-[width,background-color] duration-200",
                i === safeIdx
                  ? "w-[18px] bg-brand-primary"
                  : "w-1.5 bg-white/10"
              )}
            />
          ))}
        </div>
      </div>

      <CarouselArrow
        direction="next"
        disabled={!canNext}
        onClick={() => goto(safeIdx + 1)}
      />
    </div>
  );
}

function CarouselArrow({
  direction,
  disabled,
  onClick,
}: {
  readonly direction: "prev" | "next";
  readonly disabled: boolean;
  readonly onClick: () => void;
}) {
  const Icon = direction === "prev" ? ChevronLeft : ChevronRight;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={direction === "prev" ? "Previous crate" : "Next crate"}
      className={cn(
        "inline-flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full border border-border bg-card text-foreground transition-opacity",
        "hover:bg-card/80 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-card"
      )}
    >
      <Icon className="h-4 w-4" />
    </button>
  );
}
