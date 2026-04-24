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
import { useRecomputeRollupsMutation } from "@/hooks/mutations/use-analytics-mutations";
import { usePermissions } from "@/hooks/use-permissions";
import { useToast } from "@/hooks/use-toast";
import { SalesMetricsCard } from "@/components/analytics";
import type { SalesSummary } from "@/types/api";
import type { Mover, MoverDirection } from "@/types/analytics";
import { LongestRunningDisplaysCard } from "./longest-running-displays-card";
import { CategoryDemandSection } from "./category-demand-section";

const MOVERS_SKELETON_COUNT = 5;

function generateMockDailySales() {
  const dailySales = [];
  const startDate = new Date("2025-01-01");
  const endDate = new Date("2025-12-31");

  for (let d = new Date(startDate); d <= endDate; d.setDate(d.getDate() + 1)) {
    const dateStr = d.toISOString().split("T")[0];
    const dayOfYear = Math.floor(
      (d.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24),
    );
    const variation = ((dayOfYear * 7) % 20) + ((dayOfYear * 3) % 10);
    const baseUnits = 35 + variation;
    const baseRevenue = baseUnits * 75 + variation * 25;
    const baseCost = Math.floor(baseRevenue * 0.7);
    dailySales.push({
      date: dateStr,
      totalUnits: baseUnits,
      totalRevenue: baseRevenue,
      totalCost: baseCost,
      totalProfit: baseRevenue - baseCost,
    });
  }
  return dailySales;
}

const MOCK_SALES_DATA: SalesSummary = {
  totalRevenue: 1250000,
  totalCost: 875000,
  totalProfit: 375000,
  totalUnits: 15000,
  periodStart: "2025-01-01",
  periodEnd: "2025-12-31",
  monthlySales: [
    { month: "2025-01", totalRevenue: 95000, totalCost: 66500, totalProfit: 28500, totalUnits: 1200 },
    { month: "2025-02", totalRevenue: 88000, totalCost: 61600, totalProfit: 26400, totalUnits: 1100 },
    { month: "2025-03", totalRevenue: 102000, totalCost: 71400, totalProfit: 30600, totalUnits: 1280 },
    { month: "2025-04", totalRevenue: 110000, totalCost: 77000, totalProfit: 33000, totalUnits: 1350 },
    { month: "2025-05", totalRevenue: 98000, totalCost: 68600, totalProfit: 29400, totalUnits: 1220 },
    { month: "2025-06", totalRevenue: 115000, totalCost: 80500, totalProfit: 34500, totalUnits: 1400 },
    { month: "2025-07", totalRevenue: 108000, totalCost: 75600, totalProfit: 32400, totalUnits: 1320 },
    { month: "2025-08", totalRevenue: 112000, totalCost: 78400, totalProfit: 33600, totalUnits: 1380 },
    { month: "2025-09", totalRevenue: 105000, totalCost: 73500, totalProfit: 31500, totalUnits: 1300 },
    { month: "2025-10", totalRevenue: 118000, totalCost: 82600, totalProfit: 35400, totalUnits: 1450 },
    { month: "2025-11", totalRevenue: 99000, totalCost: 69300, totalProfit: 29700, totalUnits: 1250 },
    { month: "2025-12", totalRevenue: 100000, totalCost: 70000, totalProfit: 30000, totalUnits: 1250 },
  ],
  dailySales: generateMockDailySales(),
};

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
                {mover.demandVelocity != null && (
                  <div className="text-xs font-medium text-blue-600 dark:text-blue-400 whitespace-nowrap">
                    {mover.demandVelocity.toFixed(1)}/day
                  </div>
                )}
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
  const { isAdmin } = usePermissions();
  const { toast } = useToast();
  const recomputeMutation = useRecomputeRollupsMutation();

  const handleRecomputeRollups = () => {
    recomputeMutation.mutate(undefined, {
      onSuccess: (data) => {
        toast({
          title: "Sales data recomputed",
          description: `Refreshed ${data.rollupsRecomputed.toLocaleString()} daily records.`,
        });
      },
      onError: (error) => {
        toast({
          title: "Recomputation failed",
          description: error.message || "Please try again.",
          variant: "destructive",
        });
      },
    });
  };

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
      {/* Sales Metrics - Total Sales + Sales Trend (admin-only, mock data for presentation) */}
      {isAdmin && (
        <SalesMetricsCard
          data={MOCK_SALES_DATA}
          isLoading={false}
          onRecomputeRollups={handleRecomputeRollups}
          isRecomputing={recomputeMutation.isPending}
          canRecompute={isAdmin}
        />
      )}

      {/* Category Demand */}
      <CategoryDemandSection />

      {/* Top Movers + Longest Running Displays */}
      <div className="grid gap-4 md:grid-cols-2">
        <MoversCard
          title="Top Movers"
          tooltip="Top items ranked by demand velocity weighted by display consistency (ACV-weighted velocity)"
          movers={insightsData?.topMovers}
          isLoading={insightsLoading}
          icon={TrendingUp}
        />
        <LongestRunningDisplaysCard />
      </div>
    </div>
  );
}