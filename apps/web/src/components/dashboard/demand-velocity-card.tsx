"use client";

import Link from "next/link";
import { TrendingUp, TrendingDown, Activity } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { DemandVelocityItem } from "@/types/dashboard";

interface DemandVelocityCardProps {
  items: DemandVelocityItem[];
  isLoading?: boolean;
}

function Sparkline({
  data,
  className,
}: {
  data: number[];
  className?: string;
}) {
  if (data.length === 0) return null;

  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const range = max - min || 1;
  const height = 20;
  const width = 60;

  const points = data.map((value, index) => {
    const x =
      data.length <= 1 ? width / 2 : (index / (data.length - 1)) * width;
    const y = height - ((value - min) / range) * height;
    return `${x},${y}`;
  });

  return (
    <svg
      width={width}
      height={height}
      className={cn("text-muted-foreground", className)}
      viewBox={`0 0 ${width} ${height}`}
    >
      <polyline
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        points={points.join(" ")}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function VelocityRow({ item }: { item: DemandVelocityItem }) {
  const isIncrease = item.changeDirection === "increase";
  const Icon = isIncrease ? TrendingUp : TrendingDown;
  const colorClass = isIncrease
    ? "text-green-600 dark:text-green-400"
    : "text-red-600 dark:text-red-400";

  return (
    <Link
      href={`/products/${item.itemId}`}
      className="flex items-center gap-3 rounded-md p-2 transition-colors hover:bg-accent"
    >
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium truncate">{item.itemName}</p>
        <p className="text-xs text-muted-foreground">{item.itemSku}</p>
      </div>
      <Sparkline
        data={item.sparklineData}
        className={isIncrease ? "text-green-500" : "text-red-500"}
      />
      <Badge
        variant="secondary"
        className={cn(
          "flex items-center gap-1 min-w-16 justify-center",
          colorClass,
        )}
      >
        <Icon className="h-3 w-3" />
        <span>{Math.abs(item.changePercent).toFixed(0)}%</span>
      </Badge>
    </Link>
  );
}

export function DemandVelocityCard({
  items,
  isLoading,
}: DemandVelocityCardProps) {
  if (isLoading) {
    return (
      <Card className="h-full">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Skeleton className="h-5 w-5" />
            <CardTitle className="text-base">
              <Skeleton className="h-5 w-32" />
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3">
                <div className="flex-1 space-y-1">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="h-3 w-20" />
                </div>
                <Skeleton className="h-5 w-15" />
                <Skeleton className="h-5 w-14" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (items.length === 0) {
    return (
      <Card className="h-full">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-muted-foreground" />
            <CardTitle className="text-base">Demand Velocity</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground py-4 text-center">
            No demand data available yet
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="h-full">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-base">Demand Velocity</CardTitle>
        </div>
        <p className="text-xs text-muted-foreground">
          Top 5 items by demand change (7-day trend)
        </p>
      </CardHeader>
      <CardContent>
        <div className="space-y-1">
          {items.map((item) => (
            <VelocityRow key={item.itemId} item={item} />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
