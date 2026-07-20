"use client";

import { Activity, Clock, DollarSign, Package, Timer, Truck, Undo2, Warehouse, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/utils/format-money";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import type { ActionItem } from "@/types/analytics";
import {
  formatDaysRemaining,
  formatDemandVelocity,
  getDemandCategory,
  getDemandCategoryStyle,
} from "@/lib/utils/format-forecast";
import { isValidImageUrl } from "./utils";
import { SEVERITY_TOKENS } from "./severity-tokens";
import { buildWhyCopy } from "./why-copy";
import { BADGE_TOOLTIPS, METRIC_TOOLTIPS } from "./help-content";
import {
  forecastAgeMs,
  formatRelativeAge,
  STALENESS_BANNER_THRESHOLD_MS,
} from "./constants";
import { TriageRowConfidence } from "./triage-row-confidence";
import { TriageRowReorderBlock } from "./triage-row-reorder-block";

interface TriageRowProps {
  item: ActionItem;
  onDismiss?: () => void;
  onRestore?: () => void;
}

const PILL =
  "shrink-0 inline-flex items-center rounded-full px-1.5 py-[2px] text-[9.5px] font-mono uppercase tracking-[0.1em] border";

export function TriageRow({ item, onDismiss, onRestore }: TriageRowProps) {
  const severity = SEVERITY_TOKENS[item.urgency];
  const why = buildWhyCopy(item);
  const ageMs = forecastAgeMs(item.computedAt);
  const ageStale = ageMs !== null && ageMs > STALENESS_BANNER_THRESHOLD_MS;
  const isFastSeller = getDemandCategory(item.demandVelocity) === "Fast seller";
  const stockoutColor = severity.text;
  const isDrop = item.demandSegment === "drop";
  const hasRevenueAtRisk = item.revenueAtRisk != null && item.revenueAtRisk > 0;
  const hasOnOrder = item.onOrderQty != null && item.onOrderQty > 0;

  return (
    <div
      className={cn(
        "group relative flex items-stretch gap-4 px-3 py-3.5 border-b border-border/60 last:border-b-0 hover:bg-muted/30 transition-colors",
        onRestore && "opacity-60",
      )}
    >
      <div className="shrink-0 self-center">
        {isValidImageUrl(item.imageUrl) ? (
          <img
            src={item.imageUrl!}
            alt={item.name}
            loading="lazy"
            className={cn(
              "h-[52px] w-[52px] rounded-lg object-cover border",
              severity.border,
            )}
          />
        ) : (
          <div
            className={cn(
              "h-[52px] w-[52px] rounded-lg flex items-center justify-center border",
              severity.bg,
              severity.border,
            )}
            aria-hidden
          >
            <Package className={cn("h-6 w-6", severity.text)} />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0 flex flex-col gap-1.5">
        <div className="flex items-center gap-2 flex-wrap">
          <h3 className="text-sm font-semibold truncate max-w-[26ch]">{item.name}</h3>
          <span
            className={cn(
              PILL,
              severity.text,
              severity.bg,
              severity.border,
            )}
          >
            {severity.label}
          </span>
          {isDrop && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium cursor-help bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">
                  Drop
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">
                Sells in bursts and sells out fast. Order per drop rather than
                by daily runway.
              </TooltipContent>
            </Tooltip>
          )}
          {isFastSeller && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span
                  className={cn(
                    "shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium cursor-help",
                    getDemandCategoryStyle("Fast seller"),
                  )}
                >
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
                <span className="shrink-0 rounded-full bg-red-600 px-2 py-[2px] text-[9.5px] font-mono font-semibold uppercase tracking-[0.1em] text-white cursor-help">
                  Overdue
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">{BADGE_TOOLTIPS.Overdue}</TooltipContent>
            </Tooltip>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11.5px] text-muted-foreground font-mono">
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="inline-flex items-center gap-1 cursor-help">
                <Warehouse className="h-3.5 w-3.5" />
                <span className="text-foreground tabular-nums">{item.currentStock}</span>
                <span>in stock</span>
              </span>
            </TooltipTrigger>
            <TooltipContent side="top">{METRIC_TOOLTIPS.currentStock}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="inline-flex items-center gap-1 cursor-help">
                <Activity className="h-3.5 w-3.5" />
                <span className="text-foreground tabular-nums">
                  {formatDemandVelocity(item.demandVelocity)}
                </span>
              </span>
            </TooltipTrigger>
            <TooltipContent side="top">{METRIC_TOOLTIPS.demandVelocity}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="inline-flex items-center gap-1 cursor-help">
                <Timer className="h-3.5 w-3.5" />
                <span className={cn("tabular-nums", stockoutColor)}>
                  {formatDaysRemaining(item.daysToStockout)}
                </span>
              </span>
            </TooltipTrigger>
            <TooltipContent side="top">{METRIC_TOOLTIPS.daysToStockout}</TooltipContent>
          </Tooltip>
          {hasRevenueAtRisk && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex items-center gap-1 cursor-help text-red-700 dark:text-red-400">
                  <DollarSign className="h-3.5 w-3.5" />
                  <span className="tabular-nums font-semibold">
                    {formatMoney(item.revenueAtRisk)}
                  </span>
                  <span>at risk</span>
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">
                Estimated sales lost over the restock window if no order is
                placed today (price × daily demand × lead time).
              </TooltipContent>
            </Tooltip>
          )}
          {hasOnOrder && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex items-center gap-1 cursor-help text-sky-700 dark:text-sky-400">
                  <Truck className="h-3.5 w-3.5" />
                  <span className="tabular-nums">{Math.round(item.onOrderQty!)}</span>
                  <span>inbound</span>
                </span>
              </TooltipTrigger>
              <TooltipContent side="top">
                Units already on a pending shipment — the suggested order
                quantity nets these out.
              </TooltipContent>
            </Tooltip>
          )}
          <Tooltip>
            <TooltipTrigger asChild>
              <span
                className={cn(
                  "inline-flex items-center gap-1 cursor-help",
                  ageStale ? "text-amber-700 dark:text-amber-400" : "",
                )}
              >
                <Clock className="h-3.5 w-3.5" />
                <span className="tabular-nums">{formatRelativeAge(ageMs)}</span>
              </span>
            </TooltipTrigger>
            <TooltipContent side="top">
              Forecast last computed{" "}
              {item.computedAt ? new Date(item.computedAt).toLocaleString() : "—"}.
              {ageStale && " The worker may be lagging."}
            </TooltipContent>
          </Tooltip>
        </div>

        <p className="text-[11.5px] leading-[1.45] text-muted-foreground max-w-[60ch]">
          <span className="font-mono uppercase tracking-[0.1em] text-foreground/60 mr-1.5">
            Why:
          </span>
          {why}
        </p>
      </div>

      <div className="hidden md:flex shrink-0 items-center">
        <TriageRowConfidence confidence={item.confidence ?? 0} />
      </div>

      <div className="hidden lg:flex shrink-0 items-center">
        <TriageRowReorderBlock item={item} />
      </div>

      {(onDismiss || onRestore) && (
        <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
          {onDismiss && (
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Dismiss prediction"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
          {onRestore && (
            <button
              type="button"
              onClick={onRestore}
              className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Restore prediction"
            >
              <Undo2 className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      )}

      <div className="lg:hidden absolute bottom-3 right-3">
        {item.suggestedReorderQty > 0 && (
          <span className="text-xs text-muted-foreground">
            <span className="font-semibold text-foreground tabular-nums">
              {item.suggestedReorderQty}
            </span>{" "}
            units
          </span>
        )}
      </div>
    </div>
  );
}
