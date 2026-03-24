"use client";

import { Package, Warehouse, Activity, Timer } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { ActionItem } from "@/types/analytics";
import { getDaysToStockoutColor, isValidImageUrl } from "./utils";

interface PredictionItemCardProps {
  item: ActionItem;
  showUrgencyColor: boolean;
}

function formatDate(dateStr: string): string {
  if (!dateStr) return "N/A";
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return "N/A";
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export function PredictionItemCard({
  item,
  showUrgencyColor,
}: PredictionItemCardProps) {
  return (
    <Card className="bg-card hover:bg-card/50 transition-colors border border-border dark:border-none shadow-none p-2 pr-3">
      <CardContent className="p-0">
        {/* Desktop layout */}
        <div className="hidden sm:flex items-center gap-4">
          {/* Thumbnail */}
          <div className="shrink-0">
            {isValidImageUrl(item.imageUrl) ? (
              <img
                src={item.imageUrl!}
                alt={item.name}
                className="h-16 w-16 rounded-lg object-cover"
              />
            ) : (
              <div className="h-16 w-16 rounded-lg bg-muted flex items-center justify-center">
                <Package className="h-8 w-8 text-muted-foreground" />
              </div>
            )}
          </div>

          {/* Product Info */}
          <div className="flex-1 min-w-0">
            <h3 className="text-base font-semibold truncate">{item.name}</h3>
            <div className="flex flex-wrap gap-8 mt-1 text-sm text-muted-foreground">
              <span className="flex items-center gap-1 font-light">
                <Warehouse className="h-4 w-4" />
                <span className="text-foreground font-medium font-mono">
                  {item.currentStock}
                </span>
                units
              </span>
              <span className="flex items-center gap-1 font-light">
                <Activity className="h-4 w-4" />
                <span className="text-foreground font-medium font-mono">
                  {item.demandVelocity?.toFixed(2) ?? "N/A"}
                </span>
                units/day
              </span>
              <span className="flex items-center gap-1 font-light">
                <Timer className="h-4 w-4" />
                <span
                  className={cn(
                    "font-medium font-mono",
                    showUrgencyColor
                      ? getDaysToStockoutColor(item.daysToStockout)
                      : "text-foreground",
                  )}
                >
                  {item.daysToStockout?.toFixed(2) ?? "N/A"}
                </span>
                days
              </span>
            </div>
          </div>

          {/* Action Info */}
          <div className="shrink-0 flex items-center gap-1.5 text-base font-semibold">
            <span className="text-muted-foreground font-normal">
              Suggestion:
            </span>
            {item.suggestedReorderQty} units{" "}
            <span className="font-light text-muted-foreground">by</span>{" "}
            {formatDate(item.suggestedOrderDate)}
          </div>
        </div>

        {/* Mobile layout */}
        <div className="flex flex-col gap-2 sm:hidden">
          {/* Row 1: Image + Name */}
          <div className="flex items-center gap-3">
            <div className="shrink-0">
              {isValidImageUrl(item.imageUrl) ? (
                <img
                  src={item.imageUrl!}
                  alt={item.name}
                  className="h-10 w-10 rounded-lg object-cover"
                />
              ) : (
                <div className="h-10 w-10 rounded-lg bg-muted flex items-center justify-center">
                  <Package className="h-5 w-5 text-muted-foreground" />
                </div>
              )}
            </div>
            <h3 className="text-sm font-semibold truncate flex-1">
              {item.name}
            </h3>
          </div>

          {/* Row 2: Metrics with justify-between */}
          <div className="flex justify-between text-xs text-muted-foreground">
            <span className="flex items-center gap-1 font-light">
              <Warehouse className="h-4 w-4" />
              <span className="text-foreground font-medium font-mono">
                {item.currentStock}
              </span>
              units
            </span>
            <span className="flex items-center gap-1 font-light">
              <Activity className="h-4 w-4" />
              <span className="text-foreground font-medium font-mono">
                {item.demandVelocity?.toFixed(2) ?? "N/A"}
              </span>
              units/day
            </span>
            <span className="flex items-center gap-1 font-light">
              <Timer className="h-4 w-4" />
              <span
                className={cn(
                  "font-medium font-mono",
                  showUrgencyColor
                    ? getDaysToStockoutColor(item.daysToStockout)
                    : "text-foreground",
                )}
              >
                {item.daysToStockout?.toFixed(2) ?? "N/A"}
              </span>
              days
            </span>
          </div>

          {/* Row 3: Action Info */}
          <div className="flex justify-center items-center gap-1.5 text-sm font-semibold">
            <span className="text-muted-foreground font-normal">
              Suggestion:
            </span>
            {item.suggestedReorderQty} units{" "}
            <span className="font-light text-muted-foreground">by</span>{" "}
            {formatDate(item.suggestedOrderDate)}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
