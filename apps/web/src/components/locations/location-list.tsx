"use client";

import { Skeleton } from "@/components/ui/skeleton";
import type { LocationWithCounts } from "@/hooks/queries/use-locations";
import { LocationCard } from "@/components/locations/location-card";

interface LocationListProps {
  items: LocationWithCounts[];
  isLoading?: boolean;
  onSelect: (item: LocationWithCounts) => void;
}

export function LocationList({ items, isLoading, onSelect }: LocationListProps) {
  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="rounded-lg border p-4">
            <Skeleton className="h-4 w-16" />
            <Skeleton className="mt-3 h-4 w-40" />
          </div>
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">No locations yet.</p>;
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {items.map((item) => (
        <LocationCard
          key={item.location.id}
          data={item}
          onClick={() => onSelect(item)}
        />
      ))}
    </div>
  );
}

