"use client";

import { useState } from "react";
import { Package, Warehouse, Activity, Timer, X, Undo2, Clock, ChevronDown } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { WhyThisNumberDrawer } from "./why-this-number-drawer";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import type { ActionItem } from "@/types/analytics";
import { getDaysToStockoutColor, isValidImageUrl } from "./utils";
import {
  formatDemandVelocity,
  formatDaysRemaining,
  getDemandCategory,
  getDemandCategoryStyle,
  formatCoverageContext,
} from "@/lib/utils/format-forecast";
import { METRIC_TOOLTIPS, BADGE_TOOLTIPS } from "./help-content";
import { forecastAgeMs, formatRelativeAge, STALENESS_BANNER_THRESHOLD_MS } from "./constants";

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

export function PredictionItemCard({ item, showUrgencyColor, onDismiss, onRestore }: PredictionItemCardProps) {
  const [expanded, setExpanded] = useState(false);
  const ageMs = forecastAgeMs(item.computedAt);
  const ageLabel = formatRelativeAge(ageMs);
  const ageStale = ageMs !== null && ageMs > STALENESS_BANNER_THRESHOLD_MS;
  const ageBadge = (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          className={cn(
            "flex items-center gap-1 font-light cursor-help text-[11px] sm:text-xs",
            ageStale ? "text-amber-600 dark:text-amber-400" : "text-muted-foreground/70",
          )}
        >
          <Clock className="h-3 w-3 sm:h-3.5 sm:w-3.5" />
          <span className="font-mono">{ageLabel}</span>
        </span>
      </TooltipTrigger>
      <TooltipContent side="top">
        Forecast last computed{" "}
        {item.computedAt ? new Date(item.computedAt).toLocaleString() : "—"}.
        {ageStale && " The worker may be lagging."}
      </TooltipContent>
    </Tooltip>
  );
  return (
    <Card className={cn(
      "bg-card hover:bg-card/50 transition-colors border border-border dark:border-none shadow-none p-2 pr-3 relative group",
      onRestore && "opacity-60",
    )}>
      <CardContent className="p-0">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className={cn(
            "absolute z-10 rounded-md p-0.5 text-muted-foreground opacity-100 sm:opacity-0 sm:group-hover:opacity-100 hover:bg-muted hover:text-foreground transition-all",
            (onDismiss || onRestore)
              ? "top-1.5 right-7 sm:top-2 sm:right-8"
              : "top-1.5 right-1.5 sm:top-2 sm:right-2",
          )}
          aria-label={expanded ? "Hide explanation" : "Show explanation"}
          aria-expanded={expanded}
        >
          <ChevronDown
            className={cn("h-4 w-4 transition-transform", expanded && "rotate-180")}
          />
        </button>
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
                loading="lazy"
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
              {getDemandCategory(item.demandVelocity) === "Fast seller" && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span className={cn(
                      "shrink-0 rounded px-1.5 py-0.5 text-xs font-medium cursor-help",
                      getDemandCategoryStyle("Fast seller")
                    )}>
                      Fast seller
                    </span>
                  </TooltipTrigger>
                  <TooltipContent side="top">
                    {BADGE_TOOLTIPS["Fast seller"]}
                  </TooltipContent>
                </Tooltip>
              )}
              {item.overdue && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span className="shrink-0 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-900/40 dark:text-red-400 cursor-help">
                      Overdue
                    </span>
                  </TooltipTrigger>
                  <TooltipContent side="top">
                    {BADGE_TOOLTIPS.Overdue}
                  </TooltipContent>
                </Tooltip>
              )}
            </div>
            <div className="flex flex-wrap gap-4 mt-1 text-sm text-muted-foreground">
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="flex items-center gap-1 font-light cursor-help">
                    <Warehouse className="h-4 w-4" />
                    <span className="text-foreground font-medium font-mono">
                      {item.currentStock}
                    </span>
                    in stock
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top">
                  {METRIC_TOOLTIPS.currentStock}
                </TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="flex items-center gap-1 font-light cursor-help">
                    <Activity className="h-4 w-4" />
                    <span className="text-foreground font-medium font-mono">
                      {formatDemandVelocity(item.demandVelocity)}
                    </span>
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top">
                  {METRIC_TOOLTIPS.demandVelocity}
                </TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="flex items-center gap-1 font-light cursor-help">
                    <Timer className="h-4 w-4" />
                    <span
                      className={cn(
                        "font-medium font-mono",
                        showUrgencyColor
                          ? getDaysToStockoutColor(item.daysToStockout)
                          : "text-foreground",
                      )}
                    >
                      {formatDaysRemaining(item.daysToStockout)}
                    </span>
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top">
                  {METRIC_TOOLTIPS.daysToStockout}
                </TooltipContent>
              </Tooltip>
              {ageBadge}
            </div>
          </div>

          {/* Action Info */}
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="shrink-0 flex items-center gap-1.5 text-base font-semibold cursor-help">
                <span className="text-muted-foreground font-normal">
                  Reorder:
                </span>
                {item.suggestedReorderQty} units{" "}
                <span className="font-normal text-muted-foreground">
                  {formatCoverageContext(item.suggestedReorderQty, item.demandVelocity)}
                </span>{" "}
                <span className="font-light text-muted-foreground">by</span>{" "}
                {formatDate(item.suggestedOrderDate)}
              </div>
            </TooltipTrigger>
            <TooltipContent side="top">
              {METRIC_TOOLTIPS.suggestion}
            </TooltipContent>
          </Tooltip>
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
                  loading="lazy"
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
                {getDemandCategory(item.demandVelocity) === "Fast seller" && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span className={cn(
                        "shrink-0 rounded px-1 py-0.5 text-[10px] font-medium cursor-help",
                        getDemandCategoryStyle("Fast seller")
                      )}>
                        Fast seller
                      </span>
                    </TooltipTrigger>
                    <TooltipContent side="top">
                      {BADGE_TOOLTIPS["Fast seller"]}
                    </TooltipContent>
                  </Tooltip>
                )}
                {item.overdue && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span className="shrink-0 rounded bg-red-100 px-1 py-0.5 text-[10px] font-semibold text-red-700 dark:bg-red-900/40 dark:text-red-400 cursor-help">
                        Overdue
                      </span>
                    </TooltipTrigger>
                    <TooltipContent side="top">
                      {BADGE_TOOLTIPS.Overdue}
                    </TooltipContent>
                  </Tooltip>
                )}
              </div>
            </div>
          </div>

          {/* Row 2: Metrics */}
          <div className="flex flex-wrap justify-between gap-y-1 text-xs text-muted-foreground">
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="flex items-center gap-1 font-light cursor-help">
                  <Warehouse className="h-3.5 w-3.5" />
                  <span className="text-foreground font-medium font-mono">
                    {item.currentStock}
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">
                {METRIC_TOOLTIPS.currentStock}
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="flex items-center gap-1 font-light cursor-help">
                  <Activity className="h-3.5 w-3.5" />
                  <span className="text-foreground font-medium font-mono">
                    {formatDemandVelocity(item.demandVelocity)}
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">
                {METRIC_TOOLTIPS.demandVelocity}
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="flex items-center gap-1 font-light cursor-help">
                  <Timer className="h-3.5 w-3.5" />
                  <span
                    className={cn(
                      "font-medium font-mono",
                      showUrgencyColor
                        ? getDaysToStockoutColor(item.daysToStockout)
                        : "text-foreground",
                    )}
                  >
                    {formatDaysRemaining(item.daysToStockout)}
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">
                {METRIC_TOOLTIPS.daysToStockout}
              </TooltipContent>
            </Tooltip>
            {ageBadge}
          </div>

          {/* Row 3: Action Info */}
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex justify-center items-center gap-1.5 text-sm font-semibold cursor-help">
                <span className="text-muted-foreground font-normal">
                  Reorder:
                </span>
                {item.suggestedReorderQty} units{" "}
                <span className="font-normal text-muted-foreground">
                  {formatCoverageContext(item.suggestedReorderQty, item.demandVelocity)}
                </span>{" "}
                <span className="font-light text-muted-foreground">by</span>{" "}
                {formatDate(item.suggestedOrderDate)}
              </div>
            </TooltipTrigger>
            <TooltipContent side="top">
              {METRIC_TOOLTIPS.suggestion}
            </TooltipContent>
          </Tooltip>
        </div>
        <WhyThisNumberDrawer itemId={item.itemId} open={expanded} />
      </CardContent>
    </Card>
  );
}
