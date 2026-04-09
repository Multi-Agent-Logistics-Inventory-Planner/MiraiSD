"use client";

/**
 * ARCHIVED: This component was part of the Insights tab before the analytics refactor.
 * It displays a bar chart showing sales patterns by day of the week over the last 90 days.
 * Kept for reference in case it needs to be restored.
 */

import { Calendar } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { DayOfWeekPattern } from "@/types/analytics";

export function DayOfWeekChart({
  patterns,
  isLoading,
}: {
  patterns?: DayOfWeekPattern[];
  isLoading: boolean;
}) {
  const skeletonHeights = [60, 75, 50, 80, 65, 90, 70];

  if (isLoading) {
    return (
      <Card className="dark:border-none">
        <CardHeader className="flex flex-row items-center justify-between pb-8">
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-4 rounded" />
            <Skeleton className="h-5 w-28" />
          </div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1">
              <Skeleton className="w-3 h-3 rounded" />
              <Skeleton className="h-3 w-10" />
            </div>
            <div className="flex items-center gap-1">
              <Skeleton className="w-3 h-3 rounded" />
              <Skeleton className="h-3 w-14" />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-2 h-40">
            {skeletonHeights.map((height, i) => (
              <div
                key={i}
                className="flex-1 h-full flex flex-col items-center gap-1"
              >
                <div className="relative w-full flex-1 flex items-end justify-center">
                  <Skeleton
                    className="w-3/4 rounded-t"
                    style={{ height: `${height}%` }}
                  />
                </div>
                <Skeleton className="h-3 w-6" />
                <Skeleton className="h-3 w-6" />
              </div>
            ))}
          </div>
          <div className="flex justify-center mt-4">
            <Skeleton className="h-3 w-64" />
          </div>
        </CardContent>
      </Card>
    );
  }

  const maxUnits = Math.max(...(patterns?.map((p) => p.totalUnits) ?? [1]));
  const maxMultiplier = Math.max(
    ...(patterns?.map((p) => p.avgDemandMultiplier) ?? [1]),
  );

  return (
    <Card className="dark:border-none">
      <CardHeader className="flex flex-row items-center justify-between pb-8">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <Calendar className="h-4 w-4 text-muted-foreground" />
          Daily Patterns
        </CardTitle>
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <div className="flex items-center gap-1">
            <div className="w-3 h-3 bg-primary/80 rounded" />
            <span>Actual</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-3 h-3 border-2 border-[#0b66c2] dark:border-[#7c3aed] rounded" />
            <span>Forecast</span>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex items-end gap-2 h-40">
          {patterns?.map((pattern) => {
            const actualHeight = (pattern.totalUnits / maxUnits) * 100;
            const forecastHeight =
              (pattern.avgDemandMultiplier / maxMultiplier) * 100;
            const deviation =
              pattern.totalUnits > 0 && pattern.avgDemandMultiplier > 0
                ? (Math.abs(
                    (pattern.percentOfWeeklyTotal / 100) * 7 -
                      pattern.avgDemandMultiplier,
                  ) /
                    pattern.avgDemandMultiplier) *
                  100
                : 0;
            const hasDeviation = deviation > 20;

            return (
              <div
                key={pattern.dayOfWeek}
                className="flex-1 h-full flex flex-col items-center gap-1"
              >
                <div className="relative w-full flex-1 flex items-end justify-center">
                  <div
                    className={cn(
                      "w-3/4 rounded-t transition-all",
                      hasDeviation
                        ? "bg-[#0b66c2]/80 dark:bg-[#7c3aed]/80"
                        : "bg-primary/80",
                    )}
                    style={{
                      height: `${actualHeight}%`,
                      minHeight: pattern.totalUnits > 0 ? "4px" : "0",
                    }}
                    title={`Actual: ${pattern.totalUnits} units`}
                  />
                  <div
                    className="absolute w-full h-1 border-t-2 border-dashed border-[#0b66c2]/70 dark:border-[#7c3aed]/70"
                    style={{
                      bottom: `${forecastHeight}%`,
                    }}
                    title={`Forecast multiplier: ${pattern.avgDemandMultiplier.toFixed(2)}x`}
                  />
                </div>
                <span className="text-xs font-medium">
                  {pattern.dayName.slice(0, 3)}
                </span>
                <span className="text-xs text-muted-foreground">
                  {pattern.percentOfWeeklyTotal.toFixed(0)}%
                </span>
              </div>
            );
          })}
        </div>
        <p className="text-xs text-muted-foreground mt-4 text-center">
          Actual sales vs forecast patterns (last 90 days)
        </p>
      </CardContent>
    </Card>
  );
}
