"use client";

import Image from "next/image";
import { ImageOff, Package } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { NotAssignedInventory } from "@/types/api";

interface NotAssignedTableProps {
  items: NotAssignedInventory[];
  isLoading: boolean;
  pageSize?: number;
}

export function NotAssignedTable({
  items,
  isLoading,
  pageSize = 10,
}: NotAssignedTableProps) {
  if (isLoading) {
    return (
      <div>
        {Array.from({ length: pageSize }).map((_, i) => (
          <div key={i} className="flex items-center gap-3 sm:gap-4 py-3 sm:py-4 border-b last:border-b-0">
            <Skeleton className="h-12 w-12 sm:h-20 sm:w-20 rounded-lg shrink-0" />
            <div className="flex-1 space-y-1.5">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3.5 w-20" />
            </div>
            <Skeleton className="h-5 w-8" />
          </div>
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Package className="h-12 w-12 text-muted-foreground/50 mb-4" />
        <p className="text-muted-foreground">No unassigned inventory items</p>
      </div>
    );
  }

  return (
    <div>
      {items.map((item) => (
        <div key={item.id} className="flex items-center gap-2 sm:gap-4 py-3 sm:py-4 border-b last:border-b-0">
          <div className="relative h-12 w-12 sm:h-20 sm:w-20 flex-shrink-0 rounded-lg overflow-hidden bg-muted">
            {item.item.imageUrl ? (
              <Image
                src={item.item.imageUrl}
                alt={item.item.name}
                fill
                sizes="(max-width: 640px) 48px, 80px"
                className="object-cover"
              />
            ) : (
              <div className="h-full w-full flex items-center justify-center">
                <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
              </div>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-medium text-xs sm:text-base truncate">{item.item.name}</p>
            <div className="flex flex-wrap gap-1 mt-1">
              <Badge variant="secondary" className="text-[10px] sm:text-xs px-1.5 py-0">
                {item.item.category.name}
              </Badge>
              <span className="font-mono text-[10px] sm:text-xs text-muted-foreground">{item.item.sku}</span>
            </div>
          </div>
          <div className="flex flex-col items-center gap-0.5 shrink-0">
            <span className="text-sm font-semibold tabular-nums">{item.quantity}</span>
            <span className="text-[10px] sm:text-xs text-muted-foreground">in stock</span>
          </div>
        </div>
      ))}
    </div>
  );
}
