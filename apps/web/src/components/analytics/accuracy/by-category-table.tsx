"use client";

import { cn } from "@/lib/utils";
import { biasRamp, wapeRamp } from "@/components/analytics/predictions";
import type { ForecastAccuracyCategoryRow } from "@/lib/api/forecasts";
import { Card } from "@/components/ui/card";
import { WapeBar } from "./wape-bar";
import { formatBias, formatPercent } from "./format";

interface ByCategoryTableProps {
  rows: ForecastAccuracyCategoryRow[];
}

export function ByCategoryTable({ rows }: ByCategoryTableProps) {
  if (!rows.length) {
    return (
      <Card className="rounded-xl border p-6 text-sm text-muted-foreground">
        No category-level accuracy yet — once forecasts and actuals overlap, the breakdown
        appears here.
      </Card>
    );
  }

  const sorted = [...rows].sort((a, b) => b.totalActualUnits - a.totalActualUnits);

  return (
    <Card className="rounded-xl border overflow-hidden p-0">
      <div className="flex items-center justify-between border-b px-5 py-3">
        <div className="flex items-baseline gap-2">
          <h3 className="text-sm font-semibold">By category</h3>
          <span className="text-[11.5px] text-muted-foreground">last 30 days</span>
        </div>
        <span className="font-mono text-[11.5px] text-muted-foreground">
          sorted by units sold
        </span>
      </div>

      <div className="grid grid-cols-[1.4fr_1.6fr_1fr_1fr_0.8fr] gap-2 bg-muted/40 px-5 py-2 text-[9.5px] font-mono uppercase tracking-[0.13em] text-muted-foreground">
        <span>Category</span>
        <span>lt-WAPE</span>
        <span className="text-right">Bias</span>
        <span className="text-right">Units sold</span>
        <span className="text-right">Windows</span>
      </div>

      <div>
        {sorted.map((row) => {
          const wape = wapeRamp(row.ltWape);
          const bias = biasRamp(row.biasUnitsPerDay);
          return (
            <div
              key={row.category}
              className="grid grid-cols-[1.4fr_1.6fr_1fr_1fr_0.8fr] items-center gap-2 border-b last:border-b-0 px-5 py-3 hover:bg-muted/20"
            >
              <span className="text-[13.5px] font-medium truncate">{row.category}</span>
              <div className="flex items-center gap-3">
                <WapeBar value={row.ltWape} />
                <span className={cn("font-mono text-xs tabular-nums w-12 text-right", wape.text)}>
                  {formatPercent(row.ltWape)}
                </span>
              </div>
              <span className={cn("text-right font-mono text-xs tabular-nums", bias)}>
                {formatBias(row.biasUnitsPerDay, "/day")}
              </span>
              <span className="text-right font-mono text-xs tabular-nums">
                {row.totalActualUnits.toLocaleString()}
              </span>
              <span className="text-right font-mono text-xs tabular-nums text-muted-foreground">
                {row.scoredWindows.toLocaleString()}
              </span>
            </div>
          );
        })}
      </div>
    </Card>
  );
}
