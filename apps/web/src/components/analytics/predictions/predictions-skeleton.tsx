"use client";

import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export function PredictionsSkeleton() {
  return (
    <Card className="bg-background border-0 rounded-none py-0 shadow-none">
      {/* Urgency Tabs Skeleton */}
      <div className="relative">
        <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />
        <div className="flex gap-6 overflow-x-auto scrollbar-none border-b dark:border-b-[0.5px] pr-6">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-4 w-16 shrink-0 mb-2" />
          ))}
        </div>
      </div>

      {/* Filter Controls Skeleton */}
      {/* Mobile */}
      <div className="flex sm:hidden items-center gap-2">
        <Skeleton className="h-9 flex-1" />
        <Skeleton className="h-8 w-10" />
      </div>
      {/* Desktop */}
      <div className="hidden sm:flex items-center gap-2">
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-9 w-40" />
        <Skeleton className="h-9 w-56" />
      </div>

      {/* Items Grid Skeleton */}
      <div className="grid gap-2 grid-cols-1">
        {Array.from({ length: 6 }).map((_, i) => (
          <Card
            key={i}
            className="bg-card border border-border dark:border-none shadow-none"
          >
            <CardContent className="px-4 py-0">
              {/* Desktop layout skeleton */}
              <div className="hidden sm:flex items-center gap-4">
                <Skeleton className="h-16 w-16 rounded-lg shrink-0" />
                <div className="flex-1 min-w-0 space-y-2">
                  <Skeleton className="h-5 w-48" />
                  <div className="flex gap-8">
                    <Skeleton className="h-4 w-20" />
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-4 w-16" />
                  </div>
                </div>
                <Skeleton className="h-5 w-44 shrink-0" />
              </div>
              {/* Mobile layout skeleton */}
              <div className="flex flex-col gap-2 sm:hidden">
                <div className="flex items-center gap-3">
                  <Skeleton className="h-10 w-10 rounded-lg shrink-0" />
                  <Skeleton className="h-4 w-32 flex-1" />
                </div>
                <div className="flex justify-between">
                  <Skeleton className="h-3 w-16" />
                  <Skeleton className="h-3 w-20" />
                  <Skeleton className="h-3 w-14" />
                </div>
                <Skeleton className="h-4 w-36 mx-auto" />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </Card>
  );
}
