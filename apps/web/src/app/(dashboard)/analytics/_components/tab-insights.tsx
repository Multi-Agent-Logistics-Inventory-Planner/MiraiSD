"use client";

import {
  Activity,
  AlertCircle,
  ArrowDown,
  ArrowRight,
  ArrowUp,
  BarChart3,
  Calendar,
  Target,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useInsights } from "@/hooks/queries/use-insights";
import type {
  CategoryPerformance,
  DayOfWeekPattern,
  Mover,
  MoverDirection,
} from "@/types/analytics";
import { LongestRunningDisplaysCard } from "./longest-running-displays-card";

function formatNumber(value: number | null, decimals: number = 1): string {
  if (value === null || value === undefined) return "N/A";
  return value.toFixed(decimals);
}

function getAccuracyColor(accuracy: number | null): string {
  if (accuracy === null) return "text-muted-foreground";
  if (accuracy >= 80) return "text-green-600";
  if (accuracy >= 60) return "text-yellow-600";
  return "text-red-600";
}

function DayOfWeekChart({
  patterns,
  isLoading,
}: {
  patterns?: DayOfWeekPattern[];
  isLoading: boolean;
}) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-40" />
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-2 h-40">
            {Array.from({ length: 7 }).map((_, i) => (
              <Skeleton key={i} className="flex-1 h-20" />
            ))}
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

function CategoryPerformanceList({
  categories,
  isLoading,
}: {
  categories?: CategoryPerformance[];
  isLoading: boolean;
}) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-40" />
        </CardHeader>
        <CardContent className="space-y-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-2 w-full" />
            </div>
          ))}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-8">
        <CardTitle className="text-sm font-medium flex items-center gap-2">
          <BarChart3 className="h-4 w-4" />
          Category Performance
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {categories?.slice(0, 5).map((category) => (
          <div key={category.categoryId} className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium">{category.categoryName}</span>
              <div className="flex items-center gap-2">
                <Activity className="h-3 w-3 text-muted-foreground" />
                <span className="text-muted-foreground">
                  {formatNumber(category.avgDemandVelocity, 2)} units/day
                </span>
              </div>
            </div>
            <Progress value={category.demandShare} className="h-2" />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{category.unitsSold} units sold</span>
              <div className="flex items-center gap-3">
                <span>{formatNumber(category.demandShare)}% of demand</span>
                <span
                  className={getAccuracyColor(category.avgForecastAccuracy)}
                >
                  <Target className="inline h-3 w-3 mr-1" />
                  {formatNumber(category.avgForecastAccuracy)}%
                </span>
              </div>
            </div>
          </div>
        ))}
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

      <CategoryPerformanceList
        categories={data?.categoryPerformance}
        isLoading={isLoading}
      />

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
