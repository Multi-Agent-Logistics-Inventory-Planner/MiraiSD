"use client";

import { useMemo } from "react";
import { useQuery, skipToken } from "@tanstack/react-query";
import { LocationType, DISPLAY_ONLY_LOCATION_TYPES, type LocationInventory } from "@/types/api";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { useProducts } from "@/hooks/queries/use-products";
import { getSiteLocations } from "@/lib/api/locations";
import { getSiteLocationInventory } from "@/lib/api/site-inventory";
import { joinSiteLocationEntriesWithCatalog } from "@/lib/api/inventory-join";

interface ResolvedLocation {
  id: string;
  locationCode: string;
}

/**
 * Site-scoped inventory at one location, joined client-side against the catalog (T-6d-6).
 * The v1 read (`getSiteLocationInventory`) returns only inventoryId/productId/quantity/
 * updatedAt (AC-5, no catalog metadata) and excludes kuji-child/CUSTOM-kuji-parent rows via
 * the same root-product filter every other v1 inventory route uses.
 *
 * For NOT_ASSIGNED (T-6d-9), resolves the site's real NOT_ASSIGNED location row through
 * `GET /api/v1/sites/{siteId}/locations?storageLocation=NOT_ASSIGNED` instead of the retired,
 * site-blind `cachedNALocationId` module cache in lib/api/inventory.ts - that cache was a real
 * cross-site bug (never keyed by site). This intentionally changes what appears at NOT_ASSIGNED
 * relative to the legacy read: kuji-child/CUSTOM-kuji-parent rows that legacy
 * `findByStorageLocation_Id` returned unfiltered no longer appear here (verified against real
 * data by `NotAssignedInventoryReadParityIT` in the backend slice).
 */
export function useLocationInventory(
  locationType: LocationType | undefined,
  locationId: string | undefined,
  locationCode?: string
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const productsQuery = useProducts();

  const isDisplayOnly = locationType ? DISPLAY_ONLY_LOCATION_TYPES.includes(locationType) : false;
  const isNotAssigned = locationType === LocationType.NOT_ASSIGNED;
  const canResolve =
    Boolean(siteId) && Boolean(locationType) && !isDisplayOnly && (isNotAssigned || Boolean(locationId));

  const resolvedLocationQuery = useQuery<ResolvedLocation | null>({
    queryKey: ["locationInventory", siteId, "resolved-location", locationType, locationId],
    queryFn: canResolve
      ? async () => {
          if (isNotAssigned) {
            const locations = await getSiteLocations(siteId as string, "NOT_ASSIGNED");
            const naLocation = locations.find((loc) => loc.locationCode === "NA") ?? locations[0];
            return naLocation ? { id: naLocation.id, locationCode: naLocation.locationCode } : null;
          }
          return { id: locationId as string, locationCode: locationCode ?? "" };
        }
      : skipToken,
    // Restores the staleTime useNotAssignedInventory used to set directly before it was
    // rewritten as a thin wrapper over this hook (review finding 6) - without it, this query
    // (and the entries query below) default to the app-wide staleTime: 0, causing a double
    // refetch (location resolution + entries) on every mount/focus.
    staleTime: 30_000,
  });

  const resolved = resolvedLocationQuery.data;

  const entriesQuery = useQuery({
    queryKey: ["locationInventory", siteId, locationType, resolved?.id],
    queryFn: siteId && resolved ? () => getSiteLocationInventory(siteId, resolved.id) : skipToken,
    staleTime: 30_000,
  });

  const data: LocationInventory[] | undefined = useMemo(() => {
    if (!entriesQuery.data || !productsQuery.data || !resolved || !locationType) {
      return undefined;
    }
    return joinSiteLocationEntriesWithCatalog(entriesQuery.data, productsQuery.data, {
      locationId: resolved.id,
      locationCode: resolved.locationCode,
      storageLocationType: locationType,
    });
  }, [entriesQuery.data, productsQuery.data, resolved, locationType]);

  return {
    data,
    /** The real, resolved location UUID - resolves the NOT_ASSIGNED virtual ID for callers
     * (adjust/transfer dialogs) that need a real id to submit a mutation, even before any
     * inventory row exists at this location. */
    resolvedLocationId: resolved?.id,
    isLoading:
      isSiteLoading ||
      resolvedLocationQuery.isLoading ||
      entriesQuery.isLoading ||
      productsQuery.isLoading,
    isError: resolvedLocationQuery.isError || entriesQuery.isError || productsQuery.isError,
    error: siteError ?? resolvedLocationQuery.error ?? entriesQuery.error ?? productsQuery.error,
    // A selected location without a trusted current site or a resolved backing location (the
    // virtual NOT_ASSIGNED case) is not an empty inventory.  Callers must keep mutations
    // disabled until a successful snapshot exists.
    isUnresolved: !canResolve || (!resolvedLocationQuery.isLoading && resolved === null),
    isReady: Boolean(data) && !resolvedLocationQuery.isError && !entriesQuery.isError && !productsQuery.isError,
    retry: () => Promise.all([
      resolvedLocationQuery.refetch(),
      entriesQuery.refetch(),
      productsQuery.refetch(),
    ]),
  };
}
