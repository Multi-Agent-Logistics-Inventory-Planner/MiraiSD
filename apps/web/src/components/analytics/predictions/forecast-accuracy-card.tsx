"use client";

import { Activity, ArrowDown, ArrowUp, Minus, TrendingDown, TrendingUp } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useForecastAccuracy } from "@/hooks/queries/use-forecast-accuracy";
import type { ForecastAccuracyCategoryRow, ForecastAccuracyWindow } from "@/lib/api/forecasts";

function formatPercent(value: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

function formatBias(value: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)} units/day`;
}

function biasDirectionLabel(bias: number | null): string {
  if (bias === null || Math.abs(bias) < 0.5) return "balanced";
  return bias < 0 ? "under-forecasting" : "over-forecasting";
}

function delta(headline: number | null, comparison: number | null) {
  if (headline === null || comparison === null) return null;
  return comparison - headline;
}

function MetricBlock({
  label,
  value,
  sublabel,
  trend,
}: {
  label: string;
  value: string;
  sublabel?: string;
  trend?: { value: number; betterWhen: "lower" | "higher" };
}) {
  let trendIcon = null;
  if (trend && Math.abs(trend.value) > 0.001) {
    const improving =
      (trend.betterWhen === "lower" && trend.value < 0) ||
      (trend.betterWhen === "higher" && trend.value > 0);
    const Icon = trend.value < 0 ? ArrowDown : ArrowUp;
    trendIcon = (
      <span
        className={`inline-flex items-center gap-0.5 text-xs ${
          improving ? "text-green-600 dark:text-green-400" : "text-amber-600 dark:text-amber-400"
        }`}
      >
        <Icon className="h-3 w-3" />
        {Math.abs(trend.value * 100).toFixed(1)}pp 7d
      </span>
    );
  } else if (trend) {
    trendIcon = (
      <span className="inline-flex items-center gap-0.5 text-xs text-muted-foreground">
        <Minus className="h-3 w-3" />
        flat 7d
      </span>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs text-muted-foreground">{label}</span>
      <div className="flex items-baseline gap-2">
        <span className="text-2xl font-semibold tabular-nums">{value}</span>
        {trendIcon}
      </div>
      {sublabel && <span className="text-xs text-muted-foreground">{sublabel}</span>}
    </div>
  );
}

function HeadlineRow({ headline, comparison }: { headline: ForecastAccuracyWindow; comparison: ForecastAccuracyWindow }) {
  const wapeDelta = delta(headline.ltWape, comparison.ltWape);
  const bias = headline.biasUnitsPerDay;
  const underPct =
    headline.scoredWindows > 0 ? headline.underPredictions / headline.scoredWindows : null;

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      <MetricBlock
        label="Lead-time WAPE (30d)"
        value={formatPercent(headline.ltWape)}
        sublabel={`vs ${formatPercent(comparison.ltWape)} last 7d`}
        trend={wapeDelta !== null ? { value: wapeDelta, betterWhen: "lower" } : undefined}
      />
      <MetricBlock
        label="Bias"
        value={formatBias(bias)}
        sublabel={biasDirectionLabel(bias)}
      />
      <MetricBlock
        label="Under-stocked windows"
        value={formatPercent(underPct)}
        sublabel={`${headline.underPredictions} of ${headline.scoredWindows} windows`}
      />
    </div>
  );
}

function CategoryTable({ rows }: { rows: ForecastAccuracyCategoryRow[] }) {
  if (!rows.length) {
    return (
      <p className="text-sm text-muted-foreground">
        No category-level accuracy yet — once forecasts and actuals overlap, breakdown appears here.
      </p>
    );
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="text-xs text-muted-foreground">
          <tr className="border-b">
            <th className="py-2 text-left font-medium">Category</th>
            <th className="py-2 text-right font-medium">lt-WAPE</th>
            <th className="py-2 text-right font-medium">Bias</th>
            <th className="py-2 text-right font-medium hidden sm:table-cell">Units sold</th>
            <th className="py-2 text-right font-medium hidden md:table-cell">Windows</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const wape = row.ltWape ?? 0;
            const wapeClass =
              wape > 0.6
                ? "text-red-600 dark:text-red-400"
                : wape > 0.3
                ? "text-amber-600 dark:text-amber-400"
                : "text-green-600 dark:text-green-400";
            return (
              <tr key={row.category} className="border-b last:border-b-0">
                <td className="py-2 font-medium">{row.category}</td>
                <td className={`py-2 text-right tabular-nums ${wapeClass}`}>{formatPercent(row.ltWape)}</td>
                <td className="py-2 text-right tabular-nums">{formatBias(row.biasUnitsPerDay)}</td>
                <td className="py-2 text-right tabular-nums hidden sm:table-cell">{row.totalActualUnits.toLocaleString()}</td>
                <td className="py-2 text-right tabular-nums hidden md:table-cell">{row.scoredWindows.toLocaleString()}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function ForecastAccuracyCard() {
  const { data, isLoading, isError } = useForecastAccuracy();

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <Activity className="h-4 w-4 text-muted-foreground" />
          Forecast accuracy
        </CardTitle>
        <p className="text-xs text-muted-foreground">
          How close 14-day lead-time totals were to actual sales. lt-WAPE = total units missed over the
          window divided by total units sold over the window; lower is better.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading && (
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        )}
        {isError && (
          <p className="text-sm text-muted-foreground">
            Could not load forecast accuracy right now.
          </p>
        )}
        {data && (
          <>
            <HeadlineRow headline={data.headline} comparison={data.comparison} />
            <div className="pt-2 border-t">
              <div className="flex items-center justify-between mb-2">
                <h4 className="text-sm font-medium">By category (last 30 days)</h4>
                <span className="text-xs text-muted-foreground inline-flex items-center gap-1">
                  {data.headline.biasUnitsPerDay !== null && data.headline.biasUnitsPerDay < -0.5 ? (
                    <><TrendingDown className="h-3 w-3" /> under-forecast</>
                  ) : data.headline.biasUnitsPerDay !== null && data.headline.biasUnitsPerDay > 0.5 ? (
                    <><TrendingUp className="h-3 w-3" /> over-forecast</>
                  ) : null}
                </span>
              </div>
              <CategoryTable rows={data.byCategory} />
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
