"use client";

import { useState } from "react";
import { Activity, AlertCircle, Calendar, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ProductThumbnail } from "@/components/products/product-thumbnail";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PERIOD_OPTIONS } from "@/lib/constants/analytics";
import { formatNumber } from "@/lib/utils/format";
import { useDemandLeaders } from "@/hooks/queries/use-demand-leaders";
import type { DemandLeader, DemandLeadersPeriod } from "@/types/analytics";

function DemandLeaderRow({
  leader,
  metricType,
}: {
  leader: DemandLeader;
  metricType: "velocity" | "stock";
}) {
  const value =
    metricType === "velocity" ? leader.demandVelocity : leader.stockVelocity;

  return (
    <div className="flex items-center gap-1 sm:gap-4 p-2 sm:p-3 rounded-lg hover:bg-muted/50 transition-colors">
      <div className="flex-shrink-0 w-8 text-center">
        <span className="text-sm font-medium text-muted-foreground">
          #{leader.rank}
        </span>
      </div>

      <ProductThumbnail
        imageUrl={leader.imageUrl}
        alt={leader.name}
        size="lg"
        fallbackVariant="package"
      />

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium truncate">{leader.name}</p>
        </div>
        <p className="text-xs text-muted-foreground">{leader.categoryName}</p>
      </div>

      <div className="flex-shrink-0 text-right space-y-1">
        <div className="flex items-center justify-end gap-1">
          {metricType === "velocity" ? (
            <Activity className="h-3 w-3 text-muted-foreground" />
          ) : (
            <Zap className="h-3 w-3 text-muted-foreground" />
          )}
          <span className="text-sm font-semibold">
            {formatNumber(value, 2)}
            <span className="text-xs font-normal text-muted-foreground ml-1">
              {metricType === "velocity" ? "/day" : "x"}
            </span>
          </span>
        </div>
        <p className="text-xs text-muted-foreground">
          {leader.periodDemand} units ({formatNumber(leader.percentOfTotal)}%)
        </p>
      </div>
    </div>
  );
}

function DemandLeadersTable({
  leaders,
  isLoading,
  metricType,
}: {
  leaders?: DemandLeader[];
  isLoading: boolean;
  metricType: "velocity" | "stock";
}) {
  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 p-3">
            <Skeleton className="h-8 w-8 rounded" />
            <Skeleton className="h-12 w-12 rounded-md" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-2 w-full" />
            </div>
            <Skeleton className="h-8 w-20" />
          </div>
        ))}
      </div>
    );
  }

  if (!leaders || leaders.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        No demand data available for this period.
      </div>
    );
  }

  return (
    <div className="space-y-1">
      {leaders.map((leader) => (
        <DemandLeaderRow
          key={leader.itemId}
          leader={leader}
          metricType={metricType}
        />
      ))}
    </div>
  );
}

export function TabDemandLeaders() {
  const [period, setPeriod] = useState<DemandLeadersPeriod>("30d");
  const { data, isLoading, isError } = useDemandLeaders(period);

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h3 className="text-lg font-semibold">Failed to load demand leaders</h3>
        <p className="text-muted-foreground">Please try again later.</p>
      </div>
    );
  }

  return (
    <Tabs defaultValue="velocity">
      <div className="flex items-center justify-between pb-4">
        <TabsList>
          <TabsTrigger value="velocity">By Demand Velocity</TabsTrigger>
          <TabsTrigger value="stock">By Stock Velocity</TabsTrigger>
        </TabsList>
        {/* Desktop: Full select dropdown */}
        <Select
          value={period}
          onValueChange={(v) => setPeriod(v as DemandLeadersPeriod)}
        >
          <SelectTrigger className="hidden md:flex w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PERIOD_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Mobile: Calendar icon button */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="outline"
              size="icon"
              className="md:hidden"
              aria-label="Select time period"
            >
              <Calendar className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuRadioGroup
              value={period}
              onValueChange={(v) => setPeriod(v as DemandLeadersPeriod)}
            >
              {PERIOD_OPTIONS.map((option) => (
                <DropdownMenuRadioItem key={option.value} value={option.value}>
                  {option.label}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <Card className="p-1 dark:border-none">
        <TabsContent value="velocity" className="mt-0">
          <DemandLeadersTable
            leaders={data?.byDemandVelocity}
            isLoading={isLoading}
            metricType="velocity"
          />
        </TabsContent>
        <TabsContent value="stock" className="mt-0">
          <DemandLeadersTable
            leaders={data?.byStockVelocity}
            isLoading={isLoading}
            metricType="stock"
          />
        </TabsContent>
      </Card>
    </Tabs>
  );
}
