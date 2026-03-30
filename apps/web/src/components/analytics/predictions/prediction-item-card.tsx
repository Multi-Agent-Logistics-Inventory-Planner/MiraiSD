"use client";

import { Package, Warehouse, Activity, Timer, X, Undo2 } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { ActionItem } from "@/types/analytics";
import { getDaysToStockoutColor, isValidImageUrl } from "./utils";

interface PredictionItemCardProps {
  item: ActionItem;
  showUrgencyColor: boolean;
  onDismiss?: () => void;
  onRestore?: () => void;
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "No order needed";
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return "N/A";
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function formatTimeAgo(isoString: string | null): string | null {
  if (!isoString) return null;
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return null;
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) return "just now";
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
}

export function PredictionItemCard({ item, showUrgencyColor, onDismiss, onRestore }: PredictionItemCardProps) {
  const forecastAge = formatTimeAgo(item.computedAt);
  return (
    <Card className={cn(
      "bg-card hover:bg-card/50 transition-colors border border-border dark:border-none shadow-none p-2 pr-3 relative group",
      onRestore && "opacity-60",
    )}>
      <CardContent className="p-0">
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            className="absolute top-1.5 right-1.5 z-10 rounded-md p-0.5 text-muted-foreground opacity-100 sm:opacity-0 sm:group-hover:opacity-100 hover:bg-muted hover:text-foreground transition-all sm:top-2 sm:right-2"
            aria-label="Dismiss prediction"
          >
            <X className="h-4 w-4" />
          </button>
        )}
        {onRestore && (
          <button
            type="button"
            onClick={onRestore}
            className="absolute top-1.5 right-1.5 z-10 rounded-md p-0.5 text-muted-foreground opacity-100 sm:opacity-0 sm:group-hover:opacity-100 hover:bg-muted hover:text-foreground transition-all sm:top-2 sm:right-2"
            aria-label="Restore prediction"
          >
            <Undo2 className="h-4 w-4" />
          </button>
        )}
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
            <div className="flex items-center gap-2">
              <h3 className="text-base font-semibold truncate">{item.name}</h3>
              {item.overdue && (
                <span className="shrink-0 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-900/40 dark:text-red-400">
                  Overdue
                </span>
              )}
            </div>
            {forecastAge && (
              <p className="text-xs text-muted-foreground">Forecast updated: {forecastAge}</p>
            )}
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
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-1.5">
                <h3 className="text-sm font-semibold truncate">{item.name}</h3>
                {item.overdue && (
                  <span className="shrink-0 rounded bg-red-100 px-1 py-0.5 text-[10px] font-semibold text-red-700 dark:bg-red-900/40 dark:text-red-400">
                    Overdue
                  </span>
                )}
              </div>
              {forecastAge && (
                <p className="text-[10px] text-muted-foreground">Forecast: {forecastAge}</p>
              )}
            </div>
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
