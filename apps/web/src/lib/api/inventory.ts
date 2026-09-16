import { apiGet } from "./client";
import { LocationType, ProductInventoryResponse } from "@/types/api";
import { getLocations } from "./locations";
import { NOT_ASSIGNED_VIRTUAL_ID } from "./not-assigned";

export { NOT_ASSIGNED_VIRTUAL_ID };

/**
 * Get the actual NA location ID for NOT_ASSIGNED inventory operations, resolved through the
 * legacy, unscoped endpoint (silently resolves to MAIN server-side).
 *
 * Only used by kuji-boxes.ts (kuji stays on legacy/global state through Phase 6 - see
 * .specs/phase-6-inventory/log.md's 6d T-6d-12 residual-debt note). Web inventory flows resolve
 * NOT_ASSIGNED through the site-scoped `resolveSiteLocationId` in lib/api/locations.ts instead
 * (T-6d-9).
 *
 * No longer caches its result: the previous module-level `cachedNALocationId` was never keyed by
 * site, so a cached MAIN id could leak into another site's resolution the moment kuji itself
 * becomes multi-site aware. Retired now rather than left as latent debt for that migration.
 */
export async function getNALocationId(): Promise<string> {
  const locations = await getLocations("NOT_ASSIGNED");

  const naLocation = locations.find((loc) => loc.locationCode === "NA");
  if (!naLocation) {
    throw new Error("NA location not found within NOT_ASSIGNED storage location");
  }

  return naLocation.id;
}

/**
 * Resolve location ID, handling the NOT_ASSIGNED virtual ID case.
 *
 * Only used by kuji-boxes.ts as of 6e (T-6e-7) - every non-Kuji web inventory flow resolves
 * NOT_ASSIGNED through the site-scoped `resolveSiteLocationId` in lib/api/locations.ts (T-6d-9);
 * the legacy, unscoped `resolveLocationId`/`getLocationInventory`/etc. call sites that used to
 * need this were deleted in the same checkpoint.
 */
export async function resolveLocationId(
  locationType: LocationType,
  locationId: string
): Promise<string> {
  if (
    locationType === LocationType.NOT_ASSIGNED &&
    locationId === NOT_ASSIGNED_VIRTUAL_ID
  ) {
    return getNALocationId();
  }
  return locationId;
}

/**
 * Fetch all inventory entries for a product across all location types in a single request.
 *
 * Legacy, unscoped, site-blind read - only the Kuji dialogs (tier-edit-dialog.tsx,
 * transfer-in-dialog.tsx, tier-draft-ui.tsx via `useProductInventoryEntries`) still call this;
 * every other inventory-entries read moved to the site-scoped v1 route in 6d
 * (`getSiteProductInventory`). Kuji's own site migration is Phase 7 (.specs/phase-6-inventory
 * spec.md AC-6 preserves the non-MAIN Kuji-unavailable gate until then) - do not delete this
 * until that migration removes its last caller.
 */
export async function getProductInventoryEntries(
  productId: string
): Promise<ProductInventoryResponse> {
  return apiGet<ProductInventoryResponse>(`/api/inventory/by-product/${productId}`);
}
