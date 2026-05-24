"use client";

import { Skeleton } from "@/components/ui/skeleton";
import { useCoinStats } from "@/hooks/queries/use-lootbox";

interface Cell {
  readonly label: string;
  readonly value: string;
  readonly unit: string;
}

function formatNumber(n: number): string {
  return new Intl.NumberFormat().format(n);
}

/**
 * Three-up KPI strip: in circulation, holders, granted in last 7d. The hairline
 * gap pattern (1px gap over a divider-colored background) gives the dividers
 * without a per-cell border.
 */
export function StatStrip() {
  const query = useCoinStats();

  if (query.isLoading || !query.data) {
    return (
      <div className="grid grid-cols-3 gap-px overflow-hidden rounded-[10px] border border-border bg-border">
        {[0, 1, 2].map((i) => (
          <div key={i} className="bg-card px-4 py-3.5">
            <Skeleton className="mb-2 h-3 w-24" />
            <Skeleton className="h-6 w-20" />
          </div>
        ))}
      </div>
    );
  }

  const cells: Cell[] = [
    {
      label: "In circulation",
      value: formatNumber(query.data.circulation),
      unit: query.data.circulation === 1 ? "coin" : "coins",
    },
    {
      label: "Holders",
      value: formatNumber(query.data.holders),
      unit: query.data.holders === 1 ? "person" : "people",
    },
    {
      label: "Granted · 7d",
      value: query.data.granted7d > 0 ? `+${formatNumber(query.data.granted7d)}` : formatNumber(query.data.granted7d),
      unit: "coins",
    },
  ];

  return (
    <div className="grid grid-cols-3 gap-px overflow-hidden rounded-[10px] border border-border bg-border">
      {cells.map((c) => (
        <div key={c.label} className="bg-card px-3 py-3 sm:px-4 sm:py-3.5">
          <div className="truncate font-mono text-[10px] uppercase tracking-[0.08em] text-muted-foreground">
            {c.label}
          </div>
          <div className="mt-1 flex flex-wrap items-baseline gap-x-1.5">
            <span className="text-[18px] font-semibold tracking-[-0.4px] tabular-nums text-foreground sm:text-[22px]">
              {c.value}
            </span>
            <span className="font-mono text-[10px] text-muted-foreground/80 sm:text-[11px]">
              {c.unit}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
