"use client";

import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Settings2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import Link from "next/link";

interface OrdersCardProps {
  overdue: number;
  active: number;
  partial: number;
  completed: number;
  isLoading?: boolean;
}

interface BarProps {
  value: number;
  label: string;
  heightPercent: number;
  pattern: "diagonal" | "dots" | "noise" | "solid";
}

function Bar({ value, label, heightPercent, pattern }: BarProps) {
  const minHeight = 16;
  const maxHeight = 70;
  const height = Math.max(minHeight, heightPercent * maxHeight);

  const getPatternStyle = () => {
    switch (pattern) {
      case "diagonal":
        return {
          backgroundImage: `repeating-linear-gradient(
            45deg,
            transparent,
            transparent 3px,
            rgba(0,0,0,0.3) 3px,
            rgba(0,0,0,0.3) 6px
          )`,
        };
      case "dots":
        return {
          backgroundImage: `radial-gradient(circle, rgba(0,0,0,0.4) 1px, transparent 1px)`,
          backgroundSize: "4px 4px",
        };
      case "noise":
        return {
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`,
          backgroundBlendMode: "overlay" as const,
        };
      case "solid":
      default:
        return {};
    }
  };

  return (
    <div className="flex flex-col items-center gap-0.5">
      <span className="text-lg font-bold text-foreground">{value}</span>
      <span className="text-[10px] text-muted-foreground">{label}</span>
      <div
        className="w-10 rounded-md bg-muted dark:bg-[#1c1c1c] mt-0.5"
        style={{ height: `${height}px`, ...getPatternStyle() }}
      />
    </div>
  );
}

export function OrdersCard({
  overdue,
  active,
  partial,
  completed,
  isLoading,
}: OrdersCardProps) {
  if (isLoading) {
    return (
      <Card className="relative overflow-hidden rounded-2xl p-5 shadow-none h-[240px] flex flex-col">
        <Skeleton className="h-6 w-16" />
        <div className="mt-3 flex items-end justify-between gap-2 flex-1">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex flex-col items-center gap-2">
              <Skeleton className="h-5 w-8" />
              <Skeleton className="h-4 w-12" />
              <Skeleton
                className="w-12 rounded-lg"
                style={{ height: `${30 + i * 20}px` }}
              />
            </div>
          ))}
        </div>
      </Card>
    );
  }

  // Calculate height percentages based on max value
  const values = [overdue, active, partial, completed];
  const maxValue = Math.max(...values, 1);
  const getHeightPercent = (value: number) => value / maxValue;

  return (
    <Card className="relative overflow-hidden rounded-2xl p-5 shadow-none h-[240px] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-foreground">Orders</h3>
        <Link href="/shipments">
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
          >
            <Settings2 className="h-4 w-4" />
          </Button>
        </Link>
      </div>

      {/* Bar chart */}
      <div className="mt-3 flex items-end justify-between gap-2 flex-1">
        <Bar
          value={overdue}
          label="Overdue"
          heightPercent={getHeightPercent(overdue)}
          pattern="diagonal"
        />
        <Bar
          value={active}
          label="Active"
          heightPercent={getHeightPercent(active)}
          pattern="dots"
        />
        <Bar
          value={partial}
          label="Partial"
          heightPercent={getHeightPercent(partial)}
          pattern="noise"
        />
        <Bar
          value={completed}
          label="Completed"
          heightPercent={getHeightPercent(completed)}
          pattern="solid"
        />
      </div>
    </Card>
  );
}
