"use client";

import { useQuery } from "@tanstack/react-query";
import type { LocationType, Inventory } from "@/types/api";
import { getInventoryByLocation } from "@/lib/api/inventory";

export function useLocationInventory(
  locationType: LocationType,
  locationId: string | undefined
) {
  return useQuery({
    queryKey: ["locationInventory", locationType, locationId],
    queryFn: () => getInventoryByLocation(locationType, locationId as string) as Promise<Inventory[]>,
    enabled: Boolean(locationId),
  });
}

