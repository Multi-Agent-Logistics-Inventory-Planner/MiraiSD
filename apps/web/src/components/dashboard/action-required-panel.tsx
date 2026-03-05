"use client";

import { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  ChevronDown,
  ChevronUp,
  AlertTriangle,
  ImageOff,
  ArrowRight,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import type { ActionRequiredItem, UrgencyLevel } from "@/types/dashboard";
import { getUrgencyLevel } from "@/types/dashboard";

const STORAGE_KEY = "dashboard-v2-action-panel-collapsed";
const CONTENT_HEIGHT = "h-[280px]";

interface ActionRequiredPanelProps {
  items: ActionRequiredItem[];
  isLoading?: boolean;
}

interface GroupedItems {
  critical: ActionRequiredItem[];
  urgent: ActionRequiredItem[];
  attention: ActionRequiredItem[];
}

const URGENCY_CONFIG: Record<
  UrgencyLevel,
  {
    label: string;
    badgeClass: string;
    countBadgeClass: string;
  }
> = {
  critical: {
    label: "Critical",
    badgeClass: "bg-red-600 text-white",
    countBadgeClass: "bg-red-600 text-white",
  },
  urgent: {
    label: "Urgent",
    badgeClass: "bg-orange-500 text-white",
    countBadgeClass: "bg-orange-500 text-white",
  },
  attention: {
    label: "Needs Attention",
    badgeClass: "bg-amber-500 text-white",
    countBadgeClass: "bg-amber-500 text-white",
  },
};

function groupByUrgency(items: ActionRequiredItem[]): GroupedItems {
  const grouped: GroupedItems = {
    critical: [],
    urgent: [],
    attention: [],
  };

  for (const item of items) {
    const level = getUrgencyLevel(item.daysToStockout);
    grouped[level].push(item);
  }

  return grouped;
}

function getDefaultTab(grouped: GroupedItems): UrgencyLevel {
  if (grouped.critical.length > 0) return "critical";
  if (grouped.urgent.length > 0) return "urgent";
  return "attention";
}

function TableHeader() {
  return (
    <div className="flex items-center gap-2 px-3 py-2 border-b text-xs font-medium text-muted-foreground sticky top-0">
      <div className="flex-1 min-w-0">Product</div>
      <div className="w-14 text-right">Stock</div>
      <div className="hidden sm:block w-16 text-right">Demand</div>
      <div className="w-16 sm:w-20 text-center">Stockout</div>
      <div className="hidden sm:block w-16 text-right">Reorder</div>
    </div>
  );
}

interface ActionItemRowProps {
  item: ActionRequiredItem;
  urgencyLevel: UrgencyLevel;
}

function ActionItemRow({ item, urgencyLevel }: ActionItemRowProps) {
  const config = URGENCY_CONFIG[urgencyLevel];

  return (
    <div className="flex items-center gap-2 py-2.5 px-3 hover:bg-[#f0eee6]/50 dark:hover:bg-muted/35 transition-colors border-b border-border/50 last:border-b-0">
      {/* Product Image + Name */}
      <div className="flex-1 min-w-0 flex items-center gap-2">
        <div className="h-8 w-8 rounded bg-muted shrink-0 overflow-hidden">
          {item.imageUrl ? (
            <Image
              src={item.imageUrl}
              alt={item.itemName}
              width={32}
              height={32}
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center">
              <ImageOff className="h-4 w-4 text-muted-foreground" />
            </div>
          )}
        </div>
        <Link href={`/products/${item.itemId}`} className="min-w-0">
          <div className="text-sm sm:text-[15px] font-medium truncate">
            {item.itemName}
          </div>
        </Link>
      </div>

      {/* Stock */}
      <div className="w-14 text-right">
        <span className="text-sm font-mono">{item.currentStock}</span>
      </div>

      {/* Daily Demand - hidden on mobile */}
      <div className="hidden sm:block w-16 text-right">
        <span className="text-sm font-mono text-muted-foreground">
          {item.dailyDemand.toFixed(1)}/d
        </span>
      </div>

      {/* Days to Stockout */}
      <div className="w-16 sm:w-20 flex justify-center">
        <Tooltip>
          <TooltipTrigger asChild>
            <Badge className={cn("font-mono text-xs", config.badgeClass)}>
              {item.daysToStockout}d
            </Badge>
          </TooltipTrigger>
          <TooltipContent>
            <p>
              <span className="font-medium">
                Stockout in {item.daysToStockout} day
                {item.daysToStockout !== 1 ? "s" : ""}
              </span>
              {item.isLeadTimeExceeded && (
                <>
                  <br />
                  <span className="text-red-400">
                    Lead time ({item.leadTimeDays}d) exceeds time to stockout.
                  </span>
                </>
              )}
            </p>
          </TooltipContent>
        </Tooltip>
      </div>

      {/* Reorder Qty - hidden on mobile */}
      <div className="hidden sm:block w-16 text-right">
        <span className="text-sm font-mono">{item.suggestedReorderQty}</span>
      </div>
    </div>
  );
}

interface TabContentListProps {
  items: ActionRequiredItem[];
  level: UrgencyLevel;
}

function TabContentList({ items, level }: TabContentListProps) {
  if (items.length === 0) {
    return (
      <div
        className={cn(
          "flex items-center justify-center text-muted-foreground",
          CONTENT_HEIGHT,
        )}
      >
        No items in this category
      </div>
    );
  }

  return (
    <div className={cn("flex flex-col", CONTENT_HEIGHT)}>
      <TableHeader />
      <div className="flex-1 overflow-y-auto">
        {items.map((item) => (
          <ActionItemRow key={item.itemId} item={item} urgencyLevel={level} />
        ))}
      </div>
    </div>
  );
}

export function ActionRequiredPanel({
  items,
  isLoading,
}: ActionRequiredPanelProps) {
  // Lazy initialization to read localStorage synchronously and avoid race condition
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window === "undefined") return true;
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored !== "false";
    } catch {
      return true;
    }
  });

  // Persist collapsed state to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(collapsed));
    } catch {
      // Ignore localStorage errors (quota exceeded, private browsing)
    }
  }, [collapsed]);

  const grouped = useMemo(() => groupByUrgency(items), [items]);
  const defaultTab = useMemo(() => getDefaultTab(grouped), [grouped]);

  const isEmpty = items.length === 0;
  const isCollapsed = isEmpty || collapsed;

  if (isLoading) {
    return (
      <Card className="py-4 shadow-none">
        <CardHeader className="flex flex-row items-center justify-between">
          <div className="flex items-center gap-2">
            <Skeleton className="h-5 w-5" />
            <Skeleton className="h-5 w-40" />
          </div>
          <Skeleton className="h-8 w-8" />
        </CardHeader>
      </Card>
    );
  }

  if (isEmpty) {
    return null;
  }

  return (
    <TooltipProvider>
      <Card className="py-4 shadow-none">
        <CardHeader className={cn(
          "flex flex-row items-center justify-between",
          !isCollapsed && "pb-2"
        )}>
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-[#d97757]" />
            <CardTitle className="text-base font-semibold">
              Action Required{" "}
              <span className="text-muted-foreground font-normal ml-1">
                ({items.length})
              </span>
            </CardTitle>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setCollapsed(!collapsed)}
            className="h-8 w-8 p-0"
            aria-label={
              isCollapsed
                ? "Expand action required panel"
                : "Collapse action required panel"
            }
            aria-expanded={!isCollapsed}
          >
            {isCollapsed ? (
              <ChevronDown className="h-4 w-4" />
            ) : (
              <ChevronUp className="h-4 w-4" />
            )}
          </Button>
        </CardHeader>

        {!isCollapsed && (
          <CardContent className="pt-0">
            <Tabs defaultValue={defaultTab}>
              <TabsList>
                <TabsTrigger
                  value="critical"
                  disabled={grouped.critical.length === 0}
                  className="gap-1.5"
                >
                  Critical
                  {grouped.critical.length > 0 && (
                    <Badge
                      className={cn(
                        "h-5 px-1.5 text-xs",
                        URGENCY_CONFIG.critical.countBadgeClass,
                      )}
                    >
                      {grouped.critical.length}
                    </Badge>
                  )}
                </TabsTrigger>
                <TabsTrigger
                  value="urgent"
                  disabled={grouped.urgent.length === 0}
                  className="gap-1.5"
                >
                  Urgent
                  {grouped.urgent.length > 0 && (
                    <Badge
                      className={cn(
                        "h-5 px-1.5 text-xs",
                        URGENCY_CONFIG.urgent.countBadgeClass,
                      )}
                    >
                      {grouped.urgent.length}
                    </Badge>
                  )}
                </TabsTrigger>
                <TabsTrigger
                  value="attention"
                  disabled={grouped.attention.length === 0}
                  className="gap-1.5"
                >
                  Attention
                  {grouped.attention.length > 0 && (
                    <Badge
                      className={cn(
                        "h-5 px-1.5 text-xs",
                        URGENCY_CONFIG.attention.countBadgeClass,
                      )}
                    >
                      {grouped.attention.length}
                    </Badge>
                  )}
                </TabsTrigger>
              </TabsList>

              <TabsContent value="critical" className="mt-2">
                <TabContentList items={grouped.critical} level="critical" />
              </TabsContent>
              <TabsContent value="urgent" className="mt-2">
                <TabContentList items={grouped.urgent} level="urgent" />
              </TabsContent>
              <TabsContent value="attention" className="mt-2">
                <TabContentList items={grouped.attention} level="attention" />
              </TabsContent>
            </Tabs>

            <div className="flex justify-end pt-2">
              <Link
                href="/products?status=low"
                className="text-sm text-muted-foreground hover:text-foreground/75 flex items-center gap-1 transition-colors"
              >
                View All
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </CardContent>
        )}
      </Card>
    </TooltipProvider>
  );
}
