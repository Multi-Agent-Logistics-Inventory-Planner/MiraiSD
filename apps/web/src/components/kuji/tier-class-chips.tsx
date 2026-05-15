"use client";

import { useMemo } from "react";
import type { KujiBoxTier } from "@/types/api";
import { cn } from "@/lib/utils";
import { compareTiers } from "./tier-palette";
import {
  formatChance,
  normalizeLabel,
  rollupByClass,
  tierClassColor,
} from "./kuji-tier-class";

export const ALL_FILTER = "All";

interface TierClassChipsProps {
  readonly tiers: readonly KujiBoxTier[];
  readonly totalActive: number;
  readonly value: string;
  readonly onChange: (next: string) => void;
}

export function TierClassChips({
  tiers,
  totalActive,
  value,
  onChange,
}: TierClassChipsProps) {
  const rollups = useMemo(() => {
    const sorted = [...tiers].sort(compareTiers);
    return rollupByClass(sorted);
  }, [tiers]);

  return (
    <div className="inline-flex items-center gap-1 rounded-[9px] border border-border bg-background p-[3px] max-w-full overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      <Chip
        active={value === ALL_FILTER}
        onClick={() => onChange(ALL_FILTER)}
      >
        <span className="capitalize">All</span>
      </Chip>

      {rollups.map((r) => {
        const color = tierClassColor(r.displayLabel);
        const active = value === r.key;
        return (
          <Chip
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
          </Chip>
        );
      })}
    </div>
  );
}

interface ChipProps {
  readonly active: boolean;
  readonly onClick: () => void;
  readonly children: React.ReactNode;
}

function Chip({ active, onClick, children }: ChipProps) {
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
