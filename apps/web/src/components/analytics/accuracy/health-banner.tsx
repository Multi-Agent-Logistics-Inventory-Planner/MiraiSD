"use client";

import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ForecastAccuracyWindow } from "@/lib/api/forecasts";
import { buildHealthBanner } from "./health-banner-copy";

interface HealthBannerProps {
  headline: ForecastAccuracyWindow;
  comparison: ForecastAccuracyWindow;
}

const VERDICT_STYLES = {
  good: {
    container:
      "bg-emerald-50 dark:bg-emerald-950/30 border-emerald-300 dark:border-emerald-800/60",
    icon: "text-emerald-600 dark:text-emerald-400",
  },
  warn: {
    container:
      "bg-amber-50 dark:bg-amber-950/30 border-amber-300 dark:border-amber-800/60",
    icon: "text-amber-600 dark:text-amber-400",
  },
  bad: {
    container: "bg-red-50 dark:bg-red-950/30 border-red-300 dark:border-red-800/60",
    icon: "text-red-600 dark:text-red-400",
  },
} as const;

export function HealthBanner({ headline, comparison }: HealthBannerProps) {
  const underStockedPct =
    headline.scoredWindows > 0 ? headline.underPredictions / headline.scoredWindows : null;
  const weekOverWeekDelta =
    headline.ltWape !== null && comparison.ltWape !== null
      ? headline.ltWape - comparison.ltWape
      : null;

  const result = buildHealthBanner({
    ltWape: headline.ltWape,
    biasUnitsPerDay: headline.biasUnitsPerDay,
    underStockedPct,
    weekOverWeekDelta,
  });

  const style = VERDICT_STYLES[result.verdict];
  const Icon = result.verdict === "good" ? CheckCircle2 : AlertTriangle;

  return (
    <div className={cn("flex gap-3 rounded-xl border px-5 py-4", style.container)}>
      <Icon className={cn("h-5 w-5 shrink-0 mt-0.5", style.icon)} aria-hidden />
      <div>
        <h3 className="text-[15px] font-semibold">{result.headline}</h3>
        <p className="mt-1 text-[13px] leading-[1.5] text-muted-foreground max-w-[70ch]">
          {result.body.map((span, i) =>
            span.bold ? (
              <strong key={i} className="font-semibold text-foreground tabular-nums">
                {span.text}
              </strong>
            ) : (
              <span key={i}>{span.text}</span>
            ),
          )}
        </p>
      </div>
    </div>
  );
}
