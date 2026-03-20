"use client";

import { useMemo } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Tooltip,
  TooltipProps,
} from "recharts";
import { cn } from "@/lib/utils";
import { PERIOD_OPTIONS } from "@/lib/constants/analytics";
import { getCategoryColor } from "@/lib/utils/category-colors";
import { Calendar, ChartPie } from "lucide-react";
import type { CategoryRanking, DemandLeadersPeriod } from "@/types/analytics";

interface CategoryDemandDonutProps {
  rankings?: CategoryRanking[];
  isLoading: boolean;
  period: DemandLeadersPeriod;
  onPeriodChange: (period: DemandLeadersPeriod) => void;
  selectedCategory: string | null;
  onCategoryClick: (categoryId: string | null) => void;
}

interface ChartSegment {
  categoryId: string;
  name: string;
  value: number;
  percentage: number;
  color: string;
}

function DarkAwareTooltip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload || payload.length === 0) return null;
  const entry = payload[0].payload as ChartSegment;
  return (
    <div className="rounded-md border bg-background px-3 py-1.5 text-xs shadow-md">
      <p className="font-medium">{entry.name}</p>
      <p className="text-muted-foreground">
        {entry.value.toLocaleString()} units ({entry.percentage.toFixed(1)}%)
      </p>
    </div>
  );
}

function LegendItem({
  segment,
  isSelected,
  onClick,
}: {
  segment: ChartSegment;
  isSelected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex items-center gap-2 rounded-md px-2 py-1 text-xs transition-colors",
        "hover:bg-accent",
        isSelected && "bg-accent ring-1 ring-primary",
      )}
    >
      <span
        className="h-3 w-3 rounded-sm"
        style={{ backgroundColor: segment.color }}
      />
      <span className="font-medium">{segment.name}</span>
      <span className="text-muted-foreground">
        {segment.percentage.toFixed(0)}%
      </span>
    </button>
  );
}

export function CategoryDemandDonut({
  rankings,
  isLoading,
  period,
  onPeriodChange,
  selectedCategory,
  onCategoryClick,
}: CategoryDemandDonutProps) {
  const chartData: ChartSegment[] = useMemo(
    () =>
      (rankings ?? []).map((cat, index) => ({
        categoryId: cat.categoryId,
        name: cat.categoryName,
        value: cat.periodDemand,
        percentage: cat.percentOfTotal,
        color: getCategoryColor(index),
      })),
    [rankings]
  );

  const totalDemand = useMemo(
    () => chartData.reduce((sum, d) => sum + d.value, 0),
    [chartData]
  );

  if (isLoading) {
    return (
      <Card className="h-full border-0 border-b-[0.5px] lg:border-b-0 lg:border-r-[0.5px] dark:border-b-0 dark:lg:border-r-0 rounded-xl shadow-none">
        <CardHeader className="shrink-0 pb-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Skeleton className="h-4 w-4 rounded" />
              <Skeleton className="h-5 w-36" />
            </div>
            <Skeleton className="h-8 w-8 rounded-md" />
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center gap-4 h-[280px]">
            <Skeleton className="h-48 w-48 rounded-full" />
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-6 w-20" />
              ))}
            </div>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!rankings || rankings.length === 0 || totalDemand === 0) {
    return (
      <Card className="h-full border-0 border-b-[0.5px] lg:border-b-0 lg:border-r-[0.5px] dark:border-b-0 dark:lg:border-r-0 rounded-xl shadow-none">
        <CardHeader className="shrink-0 pb-0">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <ChartPie className="h-4 w-4 text-muted-foreground" />
              Category Demand
            </CardTitle>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 shrink-0 text-muted-foreground hover:text-muted-foreground"
                  aria-label="Select time period"
                >
                  <Calendar className="h-4 w-4" />
                </Button>
              </PopoverTrigger>
              <PopoverContent align="end" className="w-40 p-2">
                <div className="space-y-1">
                  {PERIOD_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      onClick={() => onPeriodChange(option.value)}
                      className={cn(
                        "w-full text-left px-3 py-2 text-sm rounded-md hover:bg-muted",
                        period === option.value && "bg-muted font-medium",
                      )}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </PopoverContent>
            </Popover>
          </div>
        </CardHeader>
        <CardContent>
          <div className="h-[280px] flex items-center justify-center">
            <p className="text-sm text-muted-foreground">
              No demand data available
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const handlePieClick = (entry: ChartSegment) => {
    if (selectedCategory === entry.categoryId) {
      onCategoryClick(null);
    } else {
      onCategoryClick(entry.categoryId);
    }
  };

  return (
    <Card className="h-full border-0 border-b-[0.5px] lg:border-b-0 lg:border-r-[0.5px] dark:border-b-0 dark:lg:border-r-0 rounded-xl shadow-none">
      <CardHeader className="shrink-0 pb-0">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <ChartPie className="h-4 w-4 text-muted-foreground" />
            Category Demand
          </CardTitle>
          <Popover>
            <PopoverTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 shrink-0 text-muted-foreground hover:text-muted-foreground"
                aria-label="Select time period"
              >
                <Calendar className="h-4 w-4" />
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-40 p-2">
              <div className="space-y-1">
                {PERIOD_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    onClick={() => onPeriodChange(option.value)}
                    className={cn(
                      "w-full text-left px-3 py-2 text-sm rounded-md hover:bg-muted",
                      period === option.value && "bg-muted font-medium",
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </PopoverContent>
          </Popover>
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center gap-4 h-[280px]">
          <div className="relative h-48 w-48">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={chartData}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={75}
                  paddingAngle={2}
                  dataKey="value"
                  onClick={(_, index) => handlePieClick(chartData[index])}
                  className="cursor-pointer outline-none"
                >
                  {chartData.map((entry) => (
                    <Cell
                      key={entry.categoryId}
                      fill={entry.color}
                      stroke={
                        selectedCategory === entry.categoryId
                          ? "hsl(var(--primary))"
                          : "transparent"
                      }
                      strokeWidth={
                        selectedCategory === entry.categoryId ? 3 : 0
                      }
                      className="transition-all hover:opacity-80"
                    />
                  ))}
                </Pie>
                <Tooltip content={<DarkAwareTooltip />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div className="text-center">
                <div className="text-2xl font-bold">
                  {totalDemand.toLocaleString()}
                </div>
                <div className="text-xs text-muted-foreground">units</div>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap justify-center gap-1">
            {chartData.map((segment) => (
              <LegendItem
                key={segment.categoryId}
                segment={segment}
                isSelected={selectedCategory === segment.categoryId}
                onClick={() =>
                  onCategoryClick(
                    selectedCategory === segment.categoryId
                      ? null
                      : segment.categoryId,
                  )
                }
              />
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
