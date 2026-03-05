"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Tooltip,
  TooltipProps,
} from "recharts";
import { cn } from "@/lib/utils";
import type { RiskDistributionSegment, RiskBand } from "@/types/dashboard";
import { ChartPie } from "lucide-react";

interface RiskDistributionDonutProps {
  data: RiskDistributionSegment[];
  selectedSegment: RiskBand | null;
  onSegmentClick: (band: RiskBand | null) => void;
  isLoading?: boolean;
}

function DarkAwareTooltip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload || payload.length === 0) return null;
  const entry = payload[0].payload as RiskDistributionSegment;
  return (
    <div className="rounded-md border bg-background px-3 py-1.5 text-xs shadow-md">
      <p className="font-medium">{entry.label}</p>
      <p className="text-muted-foreground">
        {entry.count} items ({entry.percentage}%)
      </p>
    </div>
  );
}

function LegendItem({
  segment,
  isSelected,
  onClick,
}: {
  segment: RiskDistributionSegment;
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
      <span className="font-medium">{segment.label}</span>
      <span className="text-muted-foreground">{segment.count}</span>
    </button>
  );
}

export function RiskDistributionDonut({
  data,
  selectedSegment,
  onSegmentClick,
  isLoading,
}: RiskDistributionDonutProps) {
  const totalItems = data.reduce((sum, d) => sum + d.count, 0);

  if (isLoading) {
    return (
      <Card className="h-full border-0 border-b sm:border-r rounded-xl shadow-none">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <ChartPie className="h-4 w-4 text-muted-foreground" />
            Risk Distribution
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center gap-4 h-[280px]">
            <Skeleton className="h-48 w-48 rounded-full" />
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-6 w-20" />
              ))}
            </div>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (data.length === 0 || totalItems === 0) {
    return (
      <Card className="h-full border-0 border-b sm:border-r rounded-xl shadow-none">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <ChartPie className="h-4 w-4 text-muted-foreground" />
            Risk Distribution
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-[280px] flex items-center justify-center">
            <p className="text-sm text-muted-foreground">
              No forecast data available
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const handlePieClick = (entry: RiskDistributionSegment) => {
    if (selectedSegment === entry.band) {
      onSegmentClick(null);
    } else {
      onSegmentClick(entry.band);
    }
  };

  return (
    <Card className="h-full border-0 border-b sm:border-r rounded-xl shadow-none">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
            <ChartPie className="h-4 w-4 text-muted-foreground" />
            Risk Distribution
          </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center gap-4 h-[280px]">
          <div className="relative h-48 w-48">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={75}
                  paddingAngle={2}
                  dataKey="count"
                  onClick={(_, index) => handlePieClick(data[index])}
                  className="cursor-pointer outline-none"
                >
                  {data.map((entry) => (
                    <Cell
                      key={entry.band}
                      fill={entry.color}
                      stroke={
                        selectedSegment === entry.band
                          ? "hsl(var(--primary))"
                          : "transparent"
                      }
                      strokeWidth={selectedSegment === entry.band ? 3 : 0}
                      className="transition-all hover:opacity-80"
                    />
                  ))}
                </Pie>
                <Tooltip content={<DarkAwareTooltip />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div className="text-center">
                <div className="text-2xl font-bold">{totalItems}</div>
                <div className="text-xs text-muted-foreground">SKUs</div>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap justify-center gap-1">
            {data.map((segment) => (
              <LegendItem
                key={segment.band}
                segment={segment}
                isSelected={selectedSegment === segment.band}
                onClick={() =>
                  onSegmentClick(
                    selectedSegment === segment.band ? null : segment.band,
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
