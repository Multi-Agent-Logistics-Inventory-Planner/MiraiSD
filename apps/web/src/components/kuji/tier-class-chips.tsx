"use client";

import { useMemo } from "react";
import type { KujiBoxTier } from "@/types/api";
import { cn } from "@/lib/utils";
import { compareTiers, hexWithAlpha } from "./tier-palette";
import {
  buildTierClassColorMap,
  formatChance,
  normalizeLabel,
  rollupByClass,
  tierClassColor,
} from "./kuji-tier-class";

export const ALL_FILTER = "All";

type ChipVariant = "segmented" | "pills";

interface TierClassChipsProps {
  readonly tiers: readonly KujiBoxTier[];
  readonly totalActive: number;
  readonly value: string;
  readonly onChange: (next: string) => void;
  readonly variant?: ChipVariant;
}

export function TierClassChips({
  tiers,
  totalActive,
  value,
  onChange,
  variant = "segmented",
}: TierClassChipsProps) {
  const rollups = useMemo(() => {
    const sorted = [...tiers].sort(compareTiers);
    return rollupByClass(sorted);
  }, [tiers]);
  const colorMap = useMemo(() => buildTierClassColorMap(tiers), [tiers]);
  const colorFor = (label: string) =>
    colorMap.get(normalizeLabel(label)) ?? tierClassColor(label);

  if (variant === "pills") {
    return (
      <div className="flex items-center gap-1 overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <PillChip
          active={value === ALL_FILTER}
          onClick={() => onChange(ALL_FILTER)}
        >
          <span className="capitalize">All</span>
          <span className="text-[10.5px] tabular-nums text-muted-foreground">
            {totalActive}
          </span>
        </PillChip>

        {rollups.map((r) => {
          const color = colorFor(r.displayLabel);
          const active = value === r.key;
          return (
            <PillChip
              key={r.key}
              active={active}
              color={color}
              onClick={() => onChange(active ? ALL_FILTER : r.key)}
            >
              <span
                className="h-[7px] w-[7px] shrink-0 rounded-full"
                style={{ background: color }}
                aria-hidden
              />
              <span className="capitalize whitespace-nowrap max-w-[10rem] truncate">
                {r.displayLabel}
              </span>
              <span
                className="text-[10.5px] tabular-nums"
                style={{
                  color: active
                    ? "var(--muted-foreground)"
                    : "rgba(255,255,255,0.4)",
                }}
              >
                {r.active}
              </span>
              <span
                className="text-[10.5px] tabular-nums pl-1.5 ml-0.5 border-l"
                style={{
                  color: active ? color : "var(--muted-foreground)",
                  borderColor: active
                    ? hexWithAlpha(color, 0.4)
                    : "var(--border)",
                }}
              >
                {formatChance(r.active, totalActive)}
              </span>
            </PillChip>
          );
        })}
      </div>
    );
  }

  return (
    <div className="inline-flex items-center gap-1 rounded-[9px] border border-border bg-background p-[3px] max-w-full overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      <SegmentedChip
        active={value === ALL_FILTER}
        onClick={() => onChange(ALL_FILTER)}
      >
        <span className="capitalize">All</span>
      </SegmentedChip>

      {rollups.map((r) => {
        const color = tierClassColor(r.displayLabel);
        const active = value === r.key;
        return (
          <SegmentedChip
            key={r.key}
            active={active}
            onClick={() => onChange(active ? ALL_FILTER : r.key)}
          >
            <span
              className="h-1.5 w-1.5 shrink-0 rounded-full"
              style={{ background: color }}
              aria-hidden
            />
            <span className="capitalize whitespace-nowrap max-w-[10rem] truncate">
              {r.displayLabel}
            </span>
            <span
              className="text-[10.5px] tabular-nums"
              style={{ color: active ? color : "rgba(255,255,255,0.4)" }}
            >
              {formatChance(r.active, totalActive)}
            </span>
          </SegmentedChip>
        );
      })}
    </div>
  );
}

function SegmentedChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md px-2.5 py-[5px] text-xs whitespace-nowrap transition-colors",
        active
          ? "bg-accent text-foreground shadow-[0_0_0_1px_var(--border)]"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

function PillChip({
  active,
  color,
  onClick,
  children,
}: {
  active: boolean;
  color?: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  const style: React.CSSProperties | undefined =
    active && color
      ? {
          background: hexWithAlpha(color, 0.18),
          borderColor: hexWithAlpha(color, 0.55),
          color: "#fff",
        }
      : undefined;
  return (
    <button
      type="button"
      onClick={onClick}
      style={style}
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs whitespace-nowrap transition-colors",
        !active && "border-border text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

export function isClassFilter(value: string): boolean {
  return value !== ALL_FILTER;
}

export function matchesClassFilter(
  tier: KujiBoxTier,
  filter: string,
): boolean {
  if (filter === ALL_FILTER) return true;
  return normalizeLabel(tier.label) === filter;
}
