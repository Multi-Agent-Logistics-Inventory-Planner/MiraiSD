import { formatMoney, formatMoneyDecimals } from "@/lib/utils/format-money";
import { ProgressRing } from "./progress-ring";
import { BarStat } from "./bar-stat";
import type { KujiValueRollups } from "./kuji-value-rollups";

interface UniformBarProps {
  readonly valueRollups: KujiValueRollups;
  readonly showPrices: boolean;
}

export function UniformBar({ valueRollups, showPrices }: UniformBarProps) {
  const {
    valueInBox,
    valueHeld,
    valueDrawn,
    valueOriginal,
    evPerDraw,
    prizesOriginal,
    totalActive,
    totalHeld,
    totalDrawn,
  } = valueRollups;

  const inBoxSub =
    totalHeld > 0
      ? `drawable now · +${formatMoney(valueHeld)} held`
      : "drawable now";
  const drawnPct =
    prizesOriginal > 0 ? Math.round((totalDrawn / prizesOriginal) * 100) : 0;
  const evDisplay = evPerDraw == null ? "—" : formatMoneyDecimals(evPerDraw);

  return (
    <>
      {/* Mobile layout: stacked card with horizontal progress bar */}
      <div className="rounded-xl border bg-card p-4 dark:border-none md:hidden">
        <div className="mb-3 flex flex-col gap-2">
          <div className="flex items-end justify-between gap-3">
            <div className="flex flex-col gap-1">
              <span className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
                Box value
              </span>
              <div className="flex items-baseline gap-2">
                <span className="text-[28px] font-medium leading-none tabular-nums">
                  {showPrices ? formatMoney(valueOriginal) : "—"}
                </span>
                <span className="text-[11px] text-muted-foreground tabular-nums">
                  {prizesOriginal} prizes
                </span>
              </div>
            </div>
            <span className="text-[11px] text-muted-foreground tabular-nums">
              {totalDrawn}/{prizesOriginal} drawn
            </span>
          </div>
          <div
            className="h-1.5 w-full overflow-hidden rounded-full bg-border/50"
            aria-hidden
          >
            <div
              className="h-full rounded-full bg-brand-primary transition-[width] duration-500"
              style={{ width: `${drawnPct}%` }}
            />
          </div>
          <div className="flex items-center justify-between text-[11px] text-muted-foreground tabular-nums">
            <span>{drawnPct}% drawn</span>
            {showPrices ? <span>{formatMoney(valueDrawn)} paid out</span> : null}
          </div>
        </div>

        <div className="mb-3 grid grid-cols-2 gap-3">
          <div className="rounded-lg border bg-background/40 p-3 dark:border-none">
            <div className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
              In box
            </div>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="text-[22px] font-medium leading-none tabular-nums">
                {totalActive}
              </span>
              <span className="text-[11px] text-muted-foreground">slips</span>
            </div>
            {showPrices && (
              <div className="mt-1 text-[13px] tabular-nums">
                {formatMoney(valueInBox)}
              </div>
            )}
            {showPrices && totalHeld > 0 && (
              <div className="mt-0.5 text-[10.5px] text-muted-foreground tabular-nums">
                +{formatMoney(valueHeld)} held
              </div>
            )}
          </div>
          <div className="rounded-lg border bg-background/40 p-3 dark:border-none">
            <div className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
              Drawn
            </div>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="text-[22px] font-medium leading-none tabular-nums">
                {totalDrawn}
              </span>
              <span className="text-[11px] text-muted-foreground">slips</span>
            </div>
            {showPrices && (
              <div className="mt-1 text-[13px] tabular-nums">
                {formatMoney(valueDrawn)}
              </div>
            )}
          </div>
        </div>

        <div className="rounded-lg border border-brand-primary/30 bg-brand-primary/10 px-4 py-3">
          <div className="flex items-center justify-between gap-3">
            <div className="flex flex-col gap-0.5">
              <span className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
                EV per draw
              </span>
              <span className="text-[10.5px] text-muted-foreground">
                {evPerDraw == null
                  ? "no draws available"
                  : "expected payout if 1 slip drawn now"}
              </span>
            </div>
            <span className="text-[26px] font-medium leading-none tabular-nums text-brand-primary">
              {showPrices ? evDisplay : "—"}
            </span>
          </div>
        </div>
      </div>

      {/* Desktop layout: ring + horizontal stats + EV cell */}
      <div className="hidden rounded-xl border bg-card p-4 dark:border-none md:block">
        <div className="flex flex-wrap items-center gap-6">
          <ProgressRing
            value={totalDrawn}
            total={prizesOriginal}
            size={88}
            label="drawn"
          />
          <div className="h-16 w-px shrink-0 bg-border" aria-hidden />
          <div className="grid flex-1 grid-cols-1 gap-6 sm:grid-cols-3">
            <BarStat
              label="Box value"
              value={showPrices ? formatMoney(valueOriginal) : "—"}
              sub={`${prizesOriginal} prize${prizesOriginal === 1 ? "" : "s"} total`}
            />
            <BarStat
              label="In box"
              value={showPrices ? `${totalActive} slip${totalActive === 1 ? "" : "s"} · ${formatMoney(valueInBox)}` : `${totalActive} slip${totalActive === 1 ? "" : "s"}`}
              sub={showPrices ? inBoxSub : "drawable now"}
            />
            <BarStat
              label="Drawn"
              value={showPrices ? `${totalDrawn} slip${totalDrawn === 1 ? "" : "s"} · ${formatMoney(valueDrawn)}` : `${totalDrawn} slip${totalDrawn === 1 ? "" : "s"}`}
              sub="paid out this box"
            />
          </div>
          <div className="ml-auto flex flex-col items-end gap-1 border-l pl-6 text-right">
            <span className="text-[10.5px] uppercase tracking-wider text-muted-foreground">
              EV / draw
            </span>
            <span className="text-[26px] font-medium leading-none tabular-nums text-brand-primary">
              {showPrices ? evDisplay : "—"}
            </span>
            <span className="text-[11px] text-muted-foreground">
              {showPrices
                ? evPerDraw == null
                  ? "no draws available"
                  : "expected payout · 1 slip"
                : null}
            </span>
          </div>
        </div>
      </div>
    </>
  );
}
