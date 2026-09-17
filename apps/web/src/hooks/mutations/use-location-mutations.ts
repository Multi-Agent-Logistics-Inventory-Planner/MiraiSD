"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  LocationType,
  Location,
} from "@/types/api";
import { STORAGE_LOCATION_CODES } from "@/types/api";
import {
  createSiteLocation,
  updateSiteLocation,
  deleteSiteLocation,
  getSiteStorageLocations,
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
import { flushInventorySiteRefresh } from "@/hooks/realtime/inventory-refresh";

function invalidateLocations(
  qc: ReturnType<typeof useQueryClient>,
  siteId: string,
  siteCode: string | undefined,
  locationType: LocationType,
) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locations", siteId] }),
    // Phase 7 pickers still read MAIN through this legacy key. A SECOND-site
    // mutation must not invalidate that MAIN compatibility cache.
    ...(siteCode === "MAIN"
      ? [qc.invalidateQueries({ queryKey: ["locations", locationType] })]
      : []),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", siteId] }),
    qc.invalidateQueries({ queryKey: ["locationInventory", siteId] }),
    qc.invalidateQueries({ queryKey: ["productInventoryEntries", siteId] }),
  ]);
}

// --- Site-scoped inventory-at-a-location mutations (Phase 6 checkpoint 6d, T-6d-9) ---------
// R-9's resolution removed the legacy untracked PUT (silent absolute-quantity set, no
// StockMovement/audit/outbox) - there is no v1 "update" route. A caller that needs to change an
// existing row's quantity uses the audited adjust mutation (use-stock-mutations.ts) with a
// signed delta instead; these two hooks only create a brand-new row or delete one entirely.

function invalidateSiteLocationInventory(
  qc: ReturnType<typeof useQueryClient>,
  siteId: string,
  /** The created/deleted row's product ID, when known - enables the targeted-refresh lever
   * (6e, T-6e-5). Delete only has the inventory row's ID client-side, not its product, so it
   * falls back to a full totals refresh rather than a doomed lookup. */
  productId: string | undefined
) {
  return Promise.all([
    // Non-fatal (6e independent review, Blocker 2): see use-stock-mutations.ts's identical
    // comment - a refresh failure must never fail the mutation whose write already committed.
    // No bare-invalidate fallback (follow-up review, second round, same reasoning as
    // use-stock-mutations.ts): flushInventorySiteRefresh already attempts its own sequenced
    // recovery internally; an unsequenced fallback here could overwrite a newer flush's value.
    flushInventorySiteRefresh(qc, siteId, productId ? [productId] : undefined).catch(() => {
      // Best-effort; recovery (if warranted) already happened inside flushInventorySiteRefresh.
    }),
    qc.invalidateQueries({ queryKey: ["locationInventory", siteId] }),
    // Removed in 6e (T-6e-8): ["dashboardStats"] matched no real query.
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", siteId] }),
    qc.invalidateQueries({ queryKey: ["products", siteId, "site"] }),
  ]);
}

export function useCreateInventoryMutation(locationType: LocationType, locationId: string) {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    SiteLocationInventoryEntry,
    Error,
    { idempotencyKey: string; siteId: string | undefined; payload: CreateSiteLocationInventoryPayload }
  >({
    mutationFn: async ({ idempotencyKey, siteId: originSiteId, payload }) => {
      if (!originSiteId) throw new Error("No active site");
      const resolvedLocationId = await resolveSiteLocationId(originSiteId, locationType, locationId);
      return createSiteLocationInventory(originSiteId, resolvedLocationId, idempotencyKey, payload);
    },
    onSuccess: async (_data, variables) => {
      if (!variables.siteId) return;
      await invalidateSiteLocationInventory(qc, variables.siteId, variables.payload.productId);
    },
  });

  // Idempotency key generated once per mutate()/mutateAsync() call (T-6d-2) - never inside
  // mutationFn, so a would-be internal retry of the same attempt reuses this same key.
  return {
    ...mutation,
    mutate: (
      payload: CreateSiteLocationInventoryPayload,
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ idempotencyKey: newIdempotencyKey(), siteId, payload }, options),
    mutateAsync: (payload: CreateSiteLocationInventoryPayload) =>
      mutation.mutateAsync({ idempotencyKey: newIdempotencyKey(), siteId, payload }),
  };
}

export function useDeleteInventoryMutation(locationType: LocationType, locationId: string) {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    void,
    Error,
    { idempotencyKey: string; siteId: string | undefined; inventoryId: string }
  >({
    mutationFn: async ({ idempotencyKey, siteId: originSiteId, inventoryId }) => {
      if (!originSiteId) throw new Error("No active site");
      const resolvedLocationId = await resolveSiteLocationId(originSiteId, locationType, locationId);
      return deleteSiteLocationInventory(originSiteId, resolvedLocationId, inventoryId, idempotencyKey);
    },
    onSuccess: async (_data, variables) => {
      if (!variables.siteId) return;
      // No productId known client-side for a delete (only the inventory row's own ID) - falls
      // back to a full totals refresh rather than a doomed lookup.
      await invalidateSiteLocationInventory(qc, variables.siteId, undefined);
    },
  });

  return {
    ...mutation,
    mutate: (
      variables: { inventoryId: string },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ idempotencyKey: newIdempotencyKey(), siteId, ...variables }, options),
    mutateAsync: (variables: { inventoryId: string }) =>
      mutation.mutateAsync({ idempotencyKey: newIdempotencyKey(), siteId, ...variables }),
  };
}

/** Capture the initiating site in variables, including callbacks after a site change. */
function useLocationCommand<TInput, TResult>(
  locationType: LocationType,
  command: (siteId: string, input: TInput, locationType: LocationType) => Promise<TResult>,
) {
  const qc = useQueryClient();
  const { siteId, siteCode } = useCurrentSite();
  const mutation = useMutation<TResult, Error, { siteId: string | undefined; siteCode: string | undefined; locationType: LocationType; input: TInput }>({
    mutationFn: ({ siteId: originSiteId, input, locationType: originType }) => {
      if (!originSiteId) throw new Error("No active site");
      return command(originSiteId, input, originType);
    },
    onSuccess: async (_result, variables) => {
      if (variables.siteId) await invalidateLocations(qc, variables.siteId, variables.siteCode, variables.locationType);
    },
  });
  return {
    ...mutation,
    mutate: (input: TInput, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ siteId, siteCode, locationType, input }, options),
    mutateAsync: (input: TInput) => mutation.mutateAsync({ siteId, siteCode, locationType, input }),
  };
}

export function useCreateLocationMutation(locationType: LocationType) {
  return useLocationCommand<{ locationCode: string; }, Location>(locationType, async (siteId, { locationCode }, originType) => {
    const categories = await getSiteStorageLocations(siteId);
    const storageLocation = categories.find(category => category.code === STORAGE_LOCATION_CODES[originType]);
    if (!storageLocation) throw new Error("Storage category not found for this site");
    return createSiteLocation(siteId, { locationCode, storageLocationId: storageLocation.id });
  });
}

export function useUpdateLocationMutation(locationType: LocationType) {
  return useLocationCommand<{ id: string; payload: { locationCode: string } }, Location>(
    locationType,
    (siteId, { id, payload }) => updateSiteLocation(siteId, id, payload),
  );
}

export function useDeleteLocationMutation(locationType: LocationType) {
  return useLocationCommand<{ id: string }, void>(
    locationType,
    (siteId, { id }) => deleteSiteLocation(siteId, id),
  );
}
