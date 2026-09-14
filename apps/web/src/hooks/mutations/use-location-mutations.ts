"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  LocationType,
  Location,
} from "@/types/api";
import { STORAGE_LOCATION_CODES } from "@/types/api";
import {
  createLocation,
  updateLocation,
  deleteLocation,
  getStorageLocationByCode,
  resolveSiteLocationId,
} from "@/lib/api/locations";
import {
  createSiteLocationInventory,
  deleteSiteLocationInventory,
  newIdempotencyKey,
  type CreateSiteLocationInventoryPayload,
  type SiteLocationInventoryEntry,
} from "@/lib/api/site-inventory";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

function invalidateLocations(qc: ReturnType<typeof useQueryClient>, locationType: LocationType) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locations", locationType] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", locationType] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts"] }),
  ]);
}

// --- Site-scoped inventory-at-a-location mutations (Phase 6 checkpoint 6d, T-6d-9) ---------
// R-9's resolution removed the legacy untracked PUT (silent absolute-quantity set, no
// StockMovement/audit/outbox) - there is no v1 "update" route. A caller that needs to change an
// existing row's quantity uses the audited adjust mutation (use-stock-mutations.ts) with a
// signed delta instead; these two hooks only create a brand-new row or delete one entirely.

function invalidateSiteLocationInventory(
  qc: ReturnType<typeof useQueryClient>,
  siteId: string
) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locationInventory", siteId] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts"] }),
    qc.invalidateQueries({ queryKey: ["products", siteId, "site"] }),
    qc.invalidateQueries({ queryKey: ["dashboardStats"] }),
    qc.invalidateQueries({ queryKey: ["inventoryTotals", siteId] }),
  ]);
}

export function useCreateInventoryMutation(locationType: LocationType, locationId: string) {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    SiteLocationInventoryEntry,
    Error,
    { idempotencyKey: string; payload: CreateSiteLocationInventoryPayload }
  >({
    mutationFn: async ({ idempotencyKey, payload }) => {
      if (!siteId) throw new Error("No active site");
      const resolvedLocationId = await resolveSiteLocationId(siteId, locationType, locationId);
      return createSiteLocationInventory(siteId, resolvedLocationId, idempotencyKey, payload);
    },
    onSuccess: async () => {
      if (!siteId) return;
      await invalidateSiteLocationInventory(qc, siteId);
    },
  });

  // Idempotency key generated once per mutate()/mutateAsync() call (T-6d-2) - never inside
  // mutationFn, so a would-be internal retry of the same attempt reuses this same key.
  return {
    ...mutation,
    mutate: (
      payload: CreateSiteLocationInventoryPayload,
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ idempotencyKey: newIdempotencyKey(), payload }, options),
    mutateAsync: (payload: CreateSiteLocationInventoryPayload) =>
      mutation.mutateAsync({ idempotencyKey: newIdempotencyKey(), payload }),
  };
}

export function useDeleteInventoryMutation(locationType: LocationType, locationId: string) {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    void,
    Error,
    { idempotencyKey: string; inventoryId: string }
  >({
    mutationFn: async ({ idempotencyKey, inventoryId }) => {
      if (!siteId) throw new Error("No active site");
      const resolvedLocationId = await resolveSiteLocationId(siteId, locationType, locationId);
      return deleteSiteLocationInventory(siteId, resolvedLocationId, inventoryId, idempotencyKey);
    },
    onSuccess: async () => {
      if (!siteId) return;
      await invalidateSiteLocationInventory(qc, siteId);
    },
  });

  return {
    ...mutation,
    mutate: (
      variables: { inventoryId: string },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ idempotencyKey: newIdempotencyKey(), ...variables }, options),
    mutateAsync: (variables: { inventoryId: string }) =>
      mutation.mutateAsync({ idempotencyKey: newIdempotencyKey(), ...variables }),
  };
}

export function useCreateLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<Location, Error, { locationCode: string }>({
    mutationFn: async ({ locationCode }) => {
      // Look up the storage location ID for this location type
      const storageLocationCode = STORAGE_LOCATION_CODES[locationType];
      const storageLocation = await getStorageLocationByCode(storageLocationCode);

      return createLocation({
        locationCode,
        storageLocationId: storageLocation.id,
      });
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useUpdateLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<Location, Error, { id: string; payload: { locationCode: string } }>({
    mutationFn: async ({ id, payload }) => {
      return updateLocation(id, payload);
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useDeleteLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<void, Error, { id: string }>({
    mutationFn: async ({ id }) => {
      return deleteLocation(id);
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

