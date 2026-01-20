"use client";

import { useQuery } from "@tanstack/react-query";
import type { LocationType, StorageLocation, Inventory } from "@/types/api";
import { getLocationsByType } from "@/lib/api/locations";
import { getInventoryByLocation } from "@/lib/api/inventory";

export interface LocationWithCounts<TLocation extends StorageLocation = StorageLocation> {
  locationType: LocationType;
  location: TLocation;
  inventoryRecords: number;
  totalQuantity: number;
}

export function useLocations(locationType: LocationType) {
  return useQuery({
    queryKey: ["locations", locationType],
    queryFn: () => getLocationsByType(locationType),
  });
}

/**
 * Convenience hook for the locations page: fetch locations and compute item counts.
 */
export function useLocationsWithCounts(locationType: LocationType) {
  return useQuery({
    queryKey: ["locationsWithCounts", locationType],
    queryFn: async (): Promise<LocationWithCounts[]> => {
      const locations = (await getLocationsByType(locationType)) as StorageLocation[];

      const inventoriesByLocation = await Promise.all(
        locations.map(async (loc) => {
          const inv = (await getInventoryByLocation(locationType, loc.id)) as Inventory[];
          return inv;
        })
      );

      return locations.map((loc, idx) => {
        const inv = inventoriesByLocation[idx] ?? [];
        const totalQuantity = inv.reduce((sum, r) => sum + (r.quantity ?? 0), 0);
        return {
          locationType,
          location: loc,
          inventoryRecords: inv.length,
          totalQuantity,
        };
      });
    },
  });
}

