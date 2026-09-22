"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { getSiteStorageLocations, type StorageLocationSummary } from "@/lib/api/locations";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

export type StorageLocationCategory = StorageLocationSummary;

/**
 * Hook to fetch all storage location categories.
 * Storage location types are fixed and seeded automatically.
 */
export function useStorageLocations() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery<StorageLocationCategory[]>({
    queryKey: ["storageLocations", siteId],
    // skipToken (rather than a `siteId!` assertion gated by `enabled`) keeps the query
    // disabled and its unresolved-siteId case visible to the type checker.
    queryFn: siteId ? () => getSiteStorageLocations(siteId) : skipToken,
    staleTime: 5 * 60 * 1000, // 5 minutes - this data rarely changes
  });

  // The site query having settled with no siteId (no error of its own - e.g. the user
  // genuinely has no active membership) must still surface as an error here, not as
  // "resolved, zero storage locations": a membership/authorization outcome is not a
  // data-seeding problem, and LocationTabs' empty-state copy ("run the database seeder")
  // would otherwise mislead an operator debugging a real access issue.
  const unresolvedSiteError =
    !isSiteLoading && !siteId && !siteError
      ? new Error("No active site membership for the current user")
      : null;

  // Without isLoading/error propagation, a disabled query (query.isLoading === false, per
  // TanStack Query v5) reads as "loaded, zero results" while /api/v1/me/sites is still
  // resolving or has failed - LocationTabs would show "no storage locations" instead of a
  // spinner, or permanently on a site-resolution error rather than surfacing it. Returning
  // only the fields consumers use (not the whole `query` object) keeps TanStack's
  // tracked-property render optimization intact for callers that do use it directly.
  return {
    data: query.data,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? unresolvedSiteError ?? query.error,
  };
}

/**
 * Hook to get the NOT_ASSIGNED storage location.
 * Returns a site-scoped storage-category ID, not a concrete location ID.
 * Currently unused. Inventory mutations resolve the virtual NOT_ASSIGNED selection
 * through resolveSiteLocationId, which returns the concrete location within this category.
 */
export function useNotAssignedStorageLocation() {
  const { data: storageLocations, ...rest } = useStorageLocations();

  const notAssignedLocation = storageLocations?.find(
    (sl) => sl.code === "NOT_ASSIGNED"
  );

  return {
    data: notAssignedLocation,
    storageLocationId: notAssignedLocation?.id,
    ...rest,
  };
}
