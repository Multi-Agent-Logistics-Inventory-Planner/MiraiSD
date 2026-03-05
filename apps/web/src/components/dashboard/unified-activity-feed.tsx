"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { formatDistanceToNowStrict } from "date-fns";
import {
  Bell,
  Package,
  ShoppingCart,
  Truck,
  RefreshCw,
  ArrowUpDown,
  ChevronDown,
  Filter,
  Check,
  Activity,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Toggle } from "@/components/ui/toggle";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { NotificationSeverity } from "@/types/api";
import type { ActivityFeedEvent, ActivityEventType } from "@/types/dashboard";

const MAX_HEIGHT = 400;

interface UnifiedActivityFeedProps {
  events: ActivityFeedEvent[];
  isLoading?: boolean;
  hasMore?: boolean;
  onLoadMore?: () => void;
  onFilterChange?: (types: ActivityEventType[], showResolved: boolean) => void;
}

const EVENT_ICONS: Record<ActivityEventType, React.ReactNode> = {
  alert: <Bell className="h-4 w-4" />,
  restock: <Package className="h-4 w-4" />,
  sale: <ShoppingCart className="h-4 w-4" />,
  shipment: <Truck className="h-4 w-4" />,
  adjustment: <ArrowUpDown className="h-4 w-4" />,
  transfer: <RefreshCw className="h-4 w-4" />,
};

const EVENT_COLORS: Record<ActivityEventType, string> = {
  alert: "text-red-500",
  restock: "text-green-500",
  sale: "text-blue-500",
  shipment: "text-purple-500",
  adjustment: "text-amber-500",
  transfer: "text-cyan-500",
};

const EVENT_LABELS: Record<ActivityEventType, string> = {
  alert: "Alerts",
  restock: "Restocks",
  sale: "Sales",
  shipment: "Shipments",
  adjustment: "Adjustments",
  transfer: "Transfers",
};

const SEVERITY_COLORS: Record<NotificationSeverity, string> = {
  [NotificationSeverity.INFO]: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300",
  [NotificationSeverity.WARNING]: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-300",
  [NotificationSeverity.CRITICAL]: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300",
};

function formatRelativeTime(timestamp: string): string {
  try {
    return formatDistanceToNowStrict(new Date(timestamp), { addSuffix: true });
  } catch {
    return "Unknown";
  }
}

function ActivityRow({ event }: { event: ActivityFeedEvent }) {
  const isResolved = event.metadata?.resolved;

  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-md p-2 transition-colors hover:bg-accent",
        isResolved && "opacity-60"
      )}
    >
      <div className={cn("mt-0.5", EVENT_COLORS[event.type])}>
        {EVENT_ICONS[event.type]}
      </div>
      <div className="flex-1 min-w-0 space-y-1">
        <p className={cn("text-sm", isResolved && "line-through")}>
          {event.title}
        </p>
        {event.description && (
          <p className="text-xs text-muted-foreground">{event.description}</p>
        )}
      </div>
      <div className="flex flex-col items-end gap-1 shrink-0">
        <span className="text-xs text-muted-foreground whitespace-nowrap">
          {formatRelativeTime(event.timestamp)}
        </span>
        {event.severity && (
          <Badge
            variant="secondary"
            className={cn("text-xs", SEVERITY_COLORS[event.severity])}
          >
            {event.severity}
          </Badge>
        )}
        {isResolved && (
          <Badge variant="outline" className="text-xs">
            <Check className="mr-1 h-3 w-3" />
            Resolved
          </Badge>
        )}
      </div>
    </div>
  );
}

export function UnifiedActivityFeed({
  events,
  isLoading,
  hasMore,
  onLoadMore,
  onFilterChange,
}: UnifiedActivityFeedProps) {
  const [selectedTypes, setSelectedTypes] = useState<ActivityEventType[]>([
    "alert",
    "restock",
    "sale",
    "shipment",
    "adjustment",
    "transfer",
  ]);
  const [showResolved, setShowResolved] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const handleTypeToggle = useCallback(
    (type: ActivityEventType) => {
      setSelectedTypes((prev) => {
        const newTypes = prev.includes(type)
          ? prev.filter((t) => t !== type)
          : [...prev, type];
        onFilterChange?.(newTypes, showResolved);
        return newTypes;
      });
    },
    [onFilterChange, showResolved]
  );

  const handleResolvedToggle = useCallback(() => {
    setShowResolved((prev) => {
      const newValue = !prev;
      onFilterChange?.(selectedTypes, newValue);
      return newValue;
    });
  }, [onFilterChange, selectedTypes]);

  // Virtualized scroll detection for loading more
  const handleScroll = useCallback(() => {
    if (!scrollRef.current || !hasMore || isLoading) return;

    const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
    if (scrollHeight - scrollTop - clientHeight < 100) {
      onLoadMore?.();
    }
  }, [hasMore, isLoading, onLoadMore]);

  useEffect(() => {
    const container = scrollRef.current;
    if (!container) return;

    container.addEventListener("scroll", handleScroll);
    return () => container.removeEventListener("scroll", handleScroll);
  }, [handleScroll]);

  if (isLoading && events.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base font-semibold flex items-center gap-2">
          <Activity className="h-4 w-4 text-muted-foreground" />
          Activity
        </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-start gap-3">
                <Skeleton className="h-4 w-4 mt-0.5" />
                <div className="flex-1 space-y-1">
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-3 w-24" />
                </div>
                <Skeleton className="h-3 w-16" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-base font-semibold flex items-center gap-2">
          <Activity className="h-4 w-4 text-muted-foreground" />
          Activity
        </CardTitle>
        <div className="flex items-center gap-2">
          <Toggle
            size="sm"
            pressed={showResolved}
            onPressedChange={handleResolvedToggle}
            className="text-xs"
          >
            Show resolved
          </Toggle>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" className="h-8">
                <Filter className="mr-1 h-3 w-3" />
                Filter
                <ChevronDown className="ml-1 h-3 w-3" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {(Object.entries(EVENT_LABELS) as [ActivityEventType, string][]).map(
                ([type, label]) => (
                  <DropdownMenuCheckboxItem
                    key={type}
                    checked={selectedTypes.includes(type)}
                    onCheckedChange={() => handleTypeToggle(type)}
                  >
                    <span className={EVENT_COLORS[type]}>{EVENT_ICONS[type]}</span>
                    <span className="ml-2">{label}</span>
                  </DropdownMenuCheckboxItem>
                )
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </CardHeader>
      <CardContent>
        {events.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">
            No activity to show
          </p>
        ) : (
          <div
            ref={scrollRef}
            className="space-y-1 overflow-y-auto"
            style={{ maxHeight: MAX_HEIGHT }}
          >
            {events.map((event) => (
              <ActivityRow key={event.id} event={event} />
            ))}
            {hasMore && (
              <div className="py-2 text-center">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onLoadMore}
                  disabled={isLoading}
                >
                  {isLoading ? "Loading..." : "Load more"}
                </Button>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
