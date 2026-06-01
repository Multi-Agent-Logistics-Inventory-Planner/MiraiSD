"use client";

import type { ForecastAccuracyWindow } from "@/lib/api/forecasts";
import { biasRamp, wapeRamp } from "@/components/analytics/predictions";
import { KpiCard } from "./kpi-card";
import { formatBias, formatPercent, biasDirectionLabel } from "./format";

interface KpiRowProps {
  headline: ForecastAccuracyWindow;
  comparison: ForecastAccuracyWindow;
}

export function KpiRow({ headline, comparison }: KpiRowProps) {
  const wapeDelta =
    headline.ltWape !== null && comparison.ltWape !== null
      ? headline.ltWape - comparison.ltWape
      : null;
  const underPct =
    headline.scoredWindows > 0 ? headline.underPredictions / headline.scoredWindows : null;

  const wape = wapeRamp(headline.ltWape);
  const bias = biasRamp(headline.biasUnitsPerDay);
  const under = wapeRamp(underPct);

  return (
    <div className="flex flex-wrap gap-4">
      <KpiCard
        label="Lead-time WAPE (30d)"
        value={formatPercent(headline.ltWape)}
        valueClassName={wape.text}
        delta={
          wapeDelta !== null
            ? {
                value: wapeDelta,
                label: `${Math.abs(wapeDelta * 100).toFixed(1)}pp vs 7d`,
                betterWhen: "lower",
              }
            : null
        }
        sub={`vs ${formatPercent(comparison.ltWape)} last 7 days`}
      />
      <KpiCard
        label="Forecast bias"
        value={formatBias(headline.biasUnitsPerDay)}
        valueClassName={bias}
        sub={biasDirectionLabel(headline.biasUnitsPerDay)}
      />
      <KpiCard
        label="Under-stocked windows"
        value={formatPercent(underPct)}
        valueClassName={under.text}
        sub={`${headline.underPredictions} of ${headline.scoredWindows} windows`}
      />
    </div>
  );
}
