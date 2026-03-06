"use client";

import { MapPin } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { LocationWithCounts } from "@/types/api";

// Column counts are all divisors of 12: full pages always produce complete rows.
// Empty grid cells on partial last pages have no styling so they're invisible whitespace.
const GRID = "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3";

interface LocationTableProps {
  items: LocationWithCounts[];
  isLoading: boolean;
  onRowClick: (item: LocationWithCounts) => void;
  pageSize?: number;
}

export function LocationTable({
  items,
  isLoading,
  onRowClick,
  pageSize = 12,
}: LocationTableProps) {
  if (isLoading) {
    return (
      <div className={GRID}>
        {Array.from({ length: pageSize }).map((_, i) => (
          <div key={i} className="rounded-xl border bg-card p-4">
            <Skeleton className="h-5 w-16 mb-3" />
            <div className="grid grid-cols-2 gap-x-2 gap-y-1">
              <Skeleton className="h-3 w-8" />
              <Skeleton className="h-3 w-8 ml-auto" />
              <Skeleton className="h-4 w-6" />
              <Skeleton className="h-4 w-8 ml-auto" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <div className="rounded-full bg-muted p-4 mb-4">
          <MapPin className="h-8 w-8 text-muted-foreground/50" />
        </div>
        <p className="text-sm font-medium text-muted-foreground">No locations found</p>
        <p className="text-xs text-muted-foreground/70 mt-1">Try a different search or add a new location.</p>
      </div>
    );
  }

  return (
    <div className={GRID}>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className={cn(
            "group relative min-w-0 cursor-pointer rounded-xl border bg-card p-3 sm:p-4 text-left transition-all",
            "hover:border-primary/40 hover:shadow-md hover:-translate-y-0.5",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
            item.totalQuantity === 0 && "opacity-55"
          )}
          onClick={() => onRowClick(item)}
        >
          {/* Occupancy status dot */}
          <span
            className={cn(
              "absolute top-3 right-3 h-2 w-2 rounded-full transition-colors",
              item.totalQuantity > 0 ? "bg-emerald-500" : "bg-muted-foreground/25"
            )}
          />

          {/* Location code */}
          <p className="font-mono text-base sm:text-sm font-bold tracking-wide leading-tight pr-4 truncate">
            {item.locationCode}
          </p>

          {/* Stats */}
          <div className="mt-3 grid grid-cols-2 gap-x-2">
            <span className="text-[10px] text-muted-foreground uppercase tracking-wide">Items</span>
            <span className="text-[10px] text-muted-foreground uppercase tracking-wide text-right">Units</span>
            <span className="text-sm font-semibold tabular-nums text-muted-foreground">{item.inventoryRecords}</span>
            <span className="text-sm font-semibold tabular-nums text-right text-muted-foreground">{item.totalQuantity}</span>
          </div>
        </button>
      ))}
    </div>
  );
}
