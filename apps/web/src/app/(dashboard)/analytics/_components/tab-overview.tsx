"use client";

import {
  AlertCircle,
  ArrowDown,
  ArrowRight,
  ArrowUp,
  HelpCircle,
  TrendingUp,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ProductThumbnail } from "@/components/products/product-thumbnail";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useInsights } from "@/hooks/queries/use-insights";
import { useSalesSummary } from "@/hooks/queries/use-analytics";
import { SalesMetricsCard } from "@/components/analytics";
import type { Mover, MoverDirection } from "@/types/analytics";
import { LongestRunningDisplaysCard } from "./longest-running-displays-card";
import { CategoryDemandSection } from "./category-demand-section";

const MOVERS_SKELETON_COUNT = 5;

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
  tooltip,
  movers,
  isLoading,
  icon: Icon,
}: {
  title: string;
  tooltip: string;
  movers?: Mover[];
  isLoading: boolean;
  icon: typeof TrendingUp;
}) {
  if (isLoading) {
    return (
      <Card className="flex flex-col overflow-hidden h-[340px] dark:border-none">
        <CardHeader className="shrink-0">
          <Skeleton className="h-5 w-32" />
        </CardHeader>
        <CardContent className="flex-1 min-h-0 space-y-3">
          {Array.from({ length: MOVERS_SKELETON_COUNT }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="flex flex-col overflow-hidden h-[340px] dark:border-none">
      <CardHeader className="flex flex-row items-center justify-between pb-2 shrink-0">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <Icon className="h-4 w-4 text-muted-foreground" />
          {title}
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <HelpCircle className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
              </TooltipTrigger>
              <TooltipContent side="top" className="max-w-[200px]">
                {tooltip}
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </CardTitle>
      </CardHeader>
      <CardContent className="flex-1 min-h-0">
        <div className="h-full overflow-y-auto space-y-2 pr-1">
          {movers?.length === 0 ? (
            <div className="h-full flex items-center justify-center">
              <p className="text-sm text-muted-foreground">
                No significant changes
              </p>
            </div>
          ) : (
            movers?.map((mover) => (
              <div
                key={mover.itemId}
                className="flex items-center gap-3 p-3 rounded-lg bg-muted/50"
              >
                <span className="text-xs font-bold text-muted-foreground w-5 shrink-0">
                  #{mover.rank}
                </span>
                <ProductThumbnail
                  imageUrl={mover.imageUrl}
                  alt={mover.name}
                  size="md"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{mover.name}</p>
                  <p className="text-xs text-muted-foreground truncate">
                    {mover.categoryName}
                  </p>
                </div>
                <div className="text-xs text-muted-foreground whitespace-nowrap">
                  {mover.previousPeriodUnits} → {mover.currentPeriodUnits}
                </div>
                <div className="flex items-center gap-1 shrink-0">
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
        </div>
      </CardContent>
    </Card>
  );
}

export function TabOverview() {
  const { data: insightsData, isLoading: insightsLoading, isError } = useInsights();
  const { data: salesData, isLoading: salesLoading } = useSalesSummary();

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h3 className="text-lg font-semibold">Failed to load overview</h3>
        <p className="text-muted-foreground">Please try again later.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Sales Metrics - Total Sales + Sales Trend */}
      <SalesMetricsCard data={salesData} isLoading={salesLoading} />

      {/* Category Demand */}
      <CategoryDemandSection />

      {/* Top Movers + Longest Running Displays */}
      <div className="grid gap-4 md:grid-cols-2">
        <MoversCard
          title="Top Movers"
          tooltip="Items with increasing demand compared to previous 30 days"
          movers={insightsData?.topMovers}
          isLoading={insightsLoading}
          icon={TrendingUp}
        />
        <LongestRunningDisplaysCard />
      </div>
    </div>
  );
}
