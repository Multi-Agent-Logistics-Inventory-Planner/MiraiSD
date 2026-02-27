"use client";

import { useQuery } from "@tanstack/react-query";
import { LocationType, type StorageLocation, type Inventory } from "@/types/api";
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
    enabled: locationType !== LocationType.NOT_ASSIGNED,
  });
}

/**
 * Fetch locations without inventory counts (single API call).
 * Use this for getting the full list for filtering/pagination.
 */
export function useLocationsOnly(locationType: LocationType) {
  return useQuery({
    queryKey: ["locationsOnly", locationType],
    queryFn: async (): Promise<StorageLocation[]> => {
      return (await getLocationsByType(locationType)) as StorageLocation[];
    },
    enabled: locationType !== LocationType.NOT_ASSIGNED,
  });
}

/**
 * @deprecated Use useLocationsWithCounts() from use-locations-with-counts.ts instead.
 * This function makes N+1 API calls and will be removed in a future version.
 *
 * Fetch inventory counts only for specific location IDs.
 * This is optimized to only fetch data for visible items (pagination).
 */
export function useLocationCounts(
  locationType: LocationType,
  locations: StorageLocation[]
) {
  return useQuery({
    queryKey: ["locationCounts", locationType, locations.map((l) => l.id)],
    queryFn: async (): Promise<Map<string, { records: number; quantity: number }>> => {
      const counts = new Map<string, { records: number; quantity: number }>();

      await Promise.all(
        locations.map(async (loc) => {
          const inv = (await getInventoryByLocation(locationType, loc.id)) as Inventory[];
          const totalQuantity = inv.reduce((sum, r) => sum + (r.quantity ?? 0), 0);
          counts.set(loc.id, { records: inv.length, quantity: totalQuantity });
        })
      );

      return counts;
    },
    enabled: locationType !== LocationType.NOT_ASSIGNED && locations.length > 0,
  });
}


