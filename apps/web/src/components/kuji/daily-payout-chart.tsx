"use client";

import { useMemo, useState } from "react";
import type { KujiDailyPayoutPoint } from "@/types/api";
import { formatMoney } from "@/lib/utils/format-money";

interface DailyPayoutChartProps {
  readonly data: readonly KujiDailyPayoutPoint[];
  readonly totals?: { valueWon: number; slipCount: number };
  readonly isLoading?: boolean;
  readonly isError?: boolean;
}

interface ChartRow {
  date: string;
  label: string;
  valueWon: number;
  slipCount: number;
  isToday: boolean;
  isYesterday: boolean;
}

function dayLabel(date: string): string {
  const d = new Date(`${date}T00:00:00`);
  if (Number.isNaN(d.getTime())) return date;
  return d.toLocaleDateString("en-US", { weekday: "short" });
}

export function DailyPayoutChart({
  data,
  totals,
  isLoading,
  isError,
}: DailyPayoutChartProps) {
  const [todayIso] = useState(() => new Date().toISOString().slice(0, 10));
  const [yesterdayIso] = useState(
    () => new Date(Date.now() - 86_400_000).toISOString().slice(0, 10),
  );

  const rows: ChartRow[] = useMemo(
    () =>
      data.map((d) => ({
        date: d.date,
        label: dayLabel(d.date),
        valueWon: d.valueWon,
        slipCount: d.slipCount,
        isToday: d.date === todayIso,
        isYesterday: d.date === yesterdayIso,
      })),
    [data, todayIso, yesterdayIso],
  );

  const total = totals?.valueWon ?? rows.reduce((s, r) => s + r.valueWon, 0);
  const avg = rows.length > 0 ? total / rows.length : 0;
  const max = useMemo(
    () => rows.reduce((m, r) => Math.max(m, r.valueWon), 0),
    [rows],
  );

  return (
    <div className="rounded-xl border bg-card p-4 dark:border-none">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-medium">Value paid out per day</div>
          <div className="text-[11px] text-muted-foreground tabular-nums">
            Last 7 days · {formatMoney(total)} total
          </div>
        </div>
        <div className="text-right">
          <div className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
            Avg / day
          </div>
          <div className="text-sm font-medium tabular-nums">
            {formatMoney(avg)}
          </div>
        </div>
      </div>
      <div className="h-44">
        {isError ? (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            Couldn&apos;t load payouts.
          </div>
        ) : isLoading ? (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            Loading…
          </div>
        ) : rows.length === 0 ? (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            No payouts yet.
          </div>
        ) : (
          <div className="relative flex h-full items-stretch gap-3">
            <div className="pointer-events-none absolute inset-x-0 inset-y-6">
              {[0.25, 0.5, 0.75, 1.0].map((p) => (
                <div
                  key={p}
                  className="absolute inset-x-0 h-px bg-border/40"
                  style={{ bottom: `${p * 100}%` }}
                />
              ))}
            </div>
            {rows.map((r) => {
              const denom = max > 0 ? max : 1;
              const heightPct = (r.valueWon / denom) * 100;
              const minHeight = r.valueWon > 0 ? 2 : 0;
              return (
                <div
                  key={r.date}
                  className="relative z-10 flex flex-1 flex-col items-center justify-end gap-1.5"
                >
                  <div
                    className="text-[11px] tabular-nums"
                    style={{ height: 14 }}
                  >
                    {r.valueWon > 0 ? (
                      <span
                        className={
                          r.isToday ? "text-foreground" : "text-muted-foreground"
                        }
                      >
                        {formatMoney(r.valueWon)}
                      </span>
                    ) : (
                      <span className="text-muted-foreground/40">—</span>
                    )}
                  </div>
                  <div
                    className="flex w-full flex-1 items-end"
                    style={{ minHeight: 0 }}
                  >
                    <div
                      className="w-full rounded-t-md"
                      style={{
                        height: `${Math.max(heightPct, minHeight)}%`,
                        background: r.isToday
                          ? "color-mix(in oklab, var(--brand-primary) 25%, transparent)"
                          : r.isYesterday
                            ? "var(--brand-primary)"
                            : "color-mix(in oklab, var(--brand-primary) 65%, transparent)",
                        border: r.isToday
                          ? "1px dashed color-mix(in oklab, var(--brand-primary) 60%, transparent)"
                          : "none",
                      }}
                    />
                  </div>
                  <div className="text-[10.5px] text-muted-foreground">
                    {r.label}
                  </div>
                  <div className="text-[10px] tabular-nums text-muted-foreground/60">
                    {r.slipCount} {r.slipCount === 1 ? "draw" : "draws"}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
