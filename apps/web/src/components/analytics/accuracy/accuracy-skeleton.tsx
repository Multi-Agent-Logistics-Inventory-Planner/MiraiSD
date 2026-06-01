"use client";

import { Skeleton } from "@/components/ui/skeleton";

export function AccuracySkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-20 w-full rounded-xl" />
      <div className="flex flex-wrap gap-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-28 flex-1 min-w-[180px] rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-10 w-full rounded-lg" />
      <Skeleton className="h-72 w-full rounded-xl" />
    </div>
  );
}
