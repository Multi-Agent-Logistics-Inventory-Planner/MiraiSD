"use client";

import {
  AlertCircle,
  ArrowDown,
  ArrowRight,
  ArrowUp,
  Calendar,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useInsights } from "@/hooks/queries/use-insights";
import type {
  DayOfWeekPattern,
  Mover,
  MoverDirection,
} from "@/types/analytics";
import { LongestRunningDisplaysCard } from "./longest-running-displays-card";
import { CategoryDemandSection } from "./category-demand-section";

function DayOfWeekChart({
  patterns,
  isLoading,
}: {
  patterns?: DayOfWeekPattern[];
  isLoading: boolean;
}) {
  const skeletonHeights = [60, 75, 50, 80, 65, 90, 70];

  if (isLoading) {
    return (
      <Card className="dark:border-0">
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
    <Card className="dark:border-0">
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

function getMoverIcon(direction: MoverDirection) {
  switch (direction) {
    case "UP":
      return <ArrowUp className="h-4 w-4 text-green-500" />;
    case "DOWN":
      return <ArrowDown className="h-4 w-4 text-red-500" />;
    default:
      return <ArrowRight className="h-4 w-4 text-muted-foreground" />;
  }
}

function getMoverColor(direction: MoverDirection): string {
  switch (direction) {
    case "UP":
      return "text-green-600";
    case "DOWN":
      return "text-red-600";
    default:
      return "text-muted-foreground";
  }
}

function MoversCard({
  title,
  movers,
  isLoading,
  icon: Icon,
}: {
  title: string;
  movers?: Mover[];
  isLoading: boolean;
  icon: typeof TrendingUp;
}) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-32" />
        </CardHeader>
        <CardContent className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium flex items-center gap-2">
          <Icon className="h-4 w-4" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {movers?.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            No significant changes
          </p>
        ) : (
          movers?.map((mover) => (
            <div
              key={mover.itemId}
              className="flex items-center justify-between p-2 rounded-lg bg-muted/50"
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{mover.name}</p>
                <p className="text-xs text-muted-foreground">
                  {mover.sku} - {mover.categoryName}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {getMoverIcon(mover.direction)}
                <span
                  className={cn(
                    "text-sm font-semibold",
                    getMoverColor(mover.direction),
                  )}
                >
                  {mover.percentChange >= 0 ? "+" : ""}
                  {mover.percentChange.toFixed(0)}%
                </span>
              </div>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}

export function TabInsights() {
  const { data, isLoading, isError } = useInsights();

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h3 className="text-lg font-semibold">Failed to load insights</h3>
        <p className="text-muted-foreground">Please try again later.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="grid gap-4 md:grid-cols-2">
        <LongestRunningDisplaysCard />
        <DayOfWeekChart
          patterns={data?.dayOfWeekPatterns}
          isLoading={isLoading}
        />
      </div>

      <CategoryDemandSection />

      <div className="grid gap-4 md:grid-cols-2">
        <MoversCard
          title="Top Movers"
          movers={data?.topMovers}
          isLoading={isLoading}
          icon={TrendingUp}
        />
        <MoversCard
          title="Declining Items"
          movers={data?.bottomMovers}
          isLoading={isLoading}
          icon={TrendingDown}
        />
      </div>
    </div>
  );
}
