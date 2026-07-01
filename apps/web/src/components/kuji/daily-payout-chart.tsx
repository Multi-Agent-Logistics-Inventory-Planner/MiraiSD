"use client";

import { useMemo, useState } from "react";
import type { KujiDailyPayoutPoint } from "@/types/api";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils";

export type DailyPayoutRange = "week" | "all";

interface DailyPayoutChartProps {
  readonly data: readonly KujiDailyPayoutPoint[];
  readonly totals?: { valueWon: number; slipCount: number };
  readonly isLoading?: boolean;
  readonly isError?: boolean;
  readonly selectedDate?: string | null;
  readonly onSelectDate?: (date: string | null) => void;
  readonly range?: DailyPayoutRange;
  readonly onRangeChange?: (range: DailyPayoutRange) => void;
  readonly canExpandRange?: boolean;
  /**
   * Strips per-bar dollar/draw labels for dense series. The dollar amount is
   * only revealed for the currently-selected bar, and a single total-draws
   * figure is shown in the subtitle. Use for long windows (>~10 days) where
   * the full labels collide with their neighbors.
   */
  readonly compact?: boolean;
  readonly showPrices?: boolean;
}

interface ChartRow {
  date: string;
  label: string;
  valueWon: number;
  slipCount: number;
  isToday: boolean;
  isYesterday: boolean;
}

function dayLabel(date: string, compact: boolean): string {
  const d = new Date(`${date}T00:00:00`);
  if (Number.isNaN(d.getTime())) return date;
  if (compact) return `${d.getMonth() + 1}/${d.getDate()}`;
  return d.toLocaleDateString("en-US", { weekday: "short" });
}

export function DailyPayoutChart({
  data,
  totals,
  isLoading,
  isError,
  selectedDate = null,
  onSelectDate,
  range = "week",
  onRangeChange,
  canExpandRange = false,
  compact = false,
  showPrices = true,
}: DailyPayoutChartProps) {
  const [todayIso] = useState(() => new Date().toISOString().slice(0, 10));
  const [yesterdayIso] = useState(
    () => new Date(Date.now() - 86_400_000).toISOString().slice(0, 10),
  );

  const rows: ChartRow[] = useMemo(
    () =>
      data.map((d) => ({
        date: d.date,
        label: dayLabel(d.date, compact),
        valueWon: d.valueWon,
        slipCount: d.slipCount,
        isToday: d.date === todayIso,
        isYesterday: d.date === yesterdayIso,
      })),
    [data, todayIso, yesterdayIso, compact],
  );

  const total = totals?.valueWon ?? rows.reduce((s, r) => s + r.valueWon, 0);
  const avg = rows.length > 0 ? total / rows.length : 0;
  const max = useMemo(
    () => rows.reduce((m, r) => Math.max(m, r.valueWon), 0),
    [rows],
  );

  const isClickable = typeof onSelectDate === "function";
  const totalSlips =
    totals?.slipCount ?? rows.reduce((s, r) => s + r.slipCount, 0);
  const baseSubtitle =
    range === "all" ? "All time" : `Last ${rows.length || 7} days`;
  const subtitle = compact
    ? showPrices
      ? `${baseSubtitle} · ${formatMoney(total)} · ${totalSlips} ${totalSlips === 1 ? "draw" : "draws"}`
      : `${baseSubtitle} · ${totalSlips} ${totalSlips === 1 ? "draw" : "draws"}`
    : showPrices
      ? `${baseSubtitle} · ${formatMoney(total)} total`
      : baseSubtitle;

  return (
    <div className="rounded-xl border bg-card p-4 dark:border-none">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-medium">Value paid out per day</div>
          <div className="text-[11px] text-muted-foreground tabular-nums">
            {subtitle}
          </div>
        </div>
        <div className="flex items-center gap-3">
          {canExpandRange && onRangeChange ? (
            <div
              role="tablist"
              aria-label="Date range"
              className="flex items-center rounded-md border bg-background p-0.5 text-[11px]"
            >
              <button
                type="button"
                role="tab"
                aria-selected={range === "week"}
                onClick={() => onRangeChange("week")}
                className={cn(
                  "rounded-sm px-2 py-0.5 tabular-nums",
                  range === "week"
                    ? "bg-muted text-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                7d
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={range === "all"}
                onClick={() => onRangeChange("all")}
                className={cn(
                  "rounded-sm px-2 py-0.5",
                  range === "all"
                    ? "bg-muted text-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                All time
              </button>
            </div>
          ) : null}
          {showPrices ? (
            <div className="text-right">
              <div className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
                Avg / day
              </div>
              <div className="text-sm font-medium tabular-nums">
                {formatMoney(avg)}
              </div>
            </div>
          ) : null}
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
          <div
            className={cn(
              "relative flex h-full items-stretch",
              compact ? "gap-1 sm:gap-1.5" : "gap-3",
            )}
          >
            <div className="pointer-events-none absolute inset-x-0 inset-y-6">
              {[0.25, 0.5, 0.75, 1.0].map((p) => (
                <div
                  key={p}
                  className="absolute inset-x-0 h-px bg-border/40"
                  style={{ bottom: `${p * 100}%` }}
                />
              ))}
            </div>
            {rows.map((r, idx) => {
              const denom = max > 0 ? max : 1;
              const heightPct = (r.valueWon / denom) * 100;
              const minHeight = r.valueWon > 0 ? 2 : 0;
              const isSelected = selectedDate === r.date;
              const hasSelection = selectedDate !== null && selectedDate !== undefined;
              const dimmed = hasSelection && !isSelected;
              const isFirst = idx === 0;
              const isLast = idx === rows.length - 1;
              // In compact mode, dense series would collide; keep only the
              // endpoints + the currently-selected bar's label visible. The
              // selection acts as a movable cursor revealing date + value.
              const showLabel =
                !compact || isFirst || isLast || isSelected;
              const barBg = isSelected
                ? "var(--brand-primary)"
                : r.isToday
                  ? "color-mix(in oklab, var(--brand-primary) 25%, transparent)"
                  : r.isYesterday
                    ? "var(--brand-primary)"
                    : "color-mix(in oklab, var(--brand-primary) 65%, transparent)";
              const barBorder = r.isToday && !isSelected
                ? "1px dashed color-mix(in oklab, var(--brand-primary) 60%, transparent)"
                : "none";
              return (
                <button
                  key={r.date}
                  type="button"
                  disabled={!isClickable}
                  onClick={() => {
                    if (!isClickable) return;
                    onSelectDate(isSelected ? null : r.date);
                  }}
                  aria-pressed={isClickable ? isSelected : undefined}
                  aria-label={`${r.label} — ${formatMoney(r.valueWon)} paid out, ${r.slipCount} ${r.slipCount === 1 ? "draw" : "draws"}`}
                  className={cn(
                    "relative z-10 flex min-w-0 flex-1 flex-col items-center justify-end gap-1.5",
                    isClickable && "cursor-pointer rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-primary/40",
                    !isClickable && "cursor-default",
                    dimmed && "opacity-40 hover:opacity-70",
                    "transition-opacity",
                  )}
                >
                  <div
                    className="text-[11px] tabular-nums"
                    style={{ height: 14 }}
                  >
                    {showPrices && r.valueWon > 0 && (!compact || isSelected) ? (
                      <span
                        className={
                          isSelected || r.isToday
                            ? "text-foreground"
                            : "text-muted-foreground"
                        }
                      >
                        {formatMoney(r.valueWon)}
                      </span>
                    ) : showPrices && !compact && r.valueWon === 0 ? (
                      <span className="text-muted-foreground/40">—</span>
                    ) : null}
                  </div>
                  <div
                    className="flex w-full flex-1 items-end"
                    style={{ minHeight: 0 }}
                  >
                    <div
                      className="w-full rounded-t-md"
                      style={{
                        height: `${Math.max(heightPct, minHeight)}%`,
                        background: barBg,
                        border: barBorder,
                        boxShadow: isSelected
                          ? "0 0 0 2px color-mix(in oklab, var(--brand-primary) 60%, transparent)"
                          : "none",
                      }}
                    />
                  </div>
                  <div
                    className={cn(
                      "text-[10.5px] tabular-nums",
                      isSelected
                        ? "font-medium text-foreground"
                        : "text-muted-foreground",
                      !showLabel && "opacity-0",
                    )}
                    aria-hidden={!showLabel}
                  >
                    {r.label}
                  </div>
                  {compact ? null : (
                    <div className="text-[10px] tabular-nums text-muted-foreground/60">
                      {r.slipCount} {r.slipCount === 1 ? "draw" : "draws"}
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
