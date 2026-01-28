"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type Inventory } from "@/types/api";
import { getInventoryByLocation } from "@/lib/api/inventory";

export function useLocationInventory(
  locationType: LocationType | undefined,
  locationId: string | undefined
) {
  return useQuery({
    queryKey: ["locationInventory", locationType, locationId],
    queryFn: () =>
      getInventoryByLocation(
        locationType as LocationType,
        locationId as string
      ) as Promise<Inventory[]>,
    enabled: Boolean(locationType) && Boolean(locationId),
  });
}

