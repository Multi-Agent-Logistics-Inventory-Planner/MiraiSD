import { apiGet } from "./client";
import { webApiClient, unwrapGeneratedResponse } from "./generated-client";
import type { components } from "@mirai/api-client";
import {
  LocationType,
  Location,
  LocationWithCounts,
  STORAGE_LOCATION_CODES,
} from "@/types/api";

import { NOT_ASSIGNED_VIRTUAL_ID } from "./not-assigned";

// Storage location types are fixed and seeded automatically.
// Use GET endpoints only - no create/update operations available.

export interface StorageLocationSummary {
  id: string;
  code: string;
  name: string;
  codePrefix?: string;
  icon?: string;
  hasDisplay: boolean;
  isDisplayOnly: boolean;
  displayOrder: number;
}

// `id`/`code` are a record's identity - a record missing either is dropped rather than
// defaulted to "", which would otherwise silently match nothing (or, worse, collide with
// another dropped record) wherever a caller keys or filters by id/code.
function toStorageLocationSummary(
  dto: components["schemas"]["StorageLocation"]
): StorageLocationSummary | null {
  if (!dto.id || !dto.code) {
    return null;
  }
  return {
    id: dto.id,
    code: dto.code,
    name: dto.name ?? "",
    codePrefix: dto.codePrefix,
    icon: dto.icon,
    hasDisplay: dto.hasDisplay ?? false,
    isDisplayOnly: dto.isDisplayOnly ?? false,
    displayOrder: dto.displayOrder ?? 0,
  };
}

// Site-scoped reads and writes use the existing generated v1 contract.

/**
 * Get all storage locations (categories like BOX_BINS, RACKS, etc.) for a site.
 */
export async function getSiteStorageLocations(siteId: string): Promise<StorageLocationSummary[]> {
  const result = await webApiClient.GET("/api/v1/sites/{siteId}/storage-locations", {
    params: { path: { siteId } },
  });
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toStorageLocationSummary)
    .filter((summary): summary is StorageLocationSummary => summary !== null);
}

// A record's identity (id + locationCode) is required to key/render it safely - see the
// toStorageLocationSummary comment above for why a missing id/code drops the row rather than
// defaulting it.
// SiteLocationController now returns SiteLocationDTO (.specs/phase-6-inventory 6e, T-6e-be-7),
// a flat DTO replacing the raw Location JPA entity it used to serialize - closes an AC-5 gap.
function toSiteLocation(dto: components["schemas"]["SiteLocationDTO"]): Location | null {
  if (!dto.id || !dto.locationCode || !dto.storageLocationId) {
    return null;
  }
  return {
    id: dto.id,
    locationCode: dto.locationCode,
    storageLocationId: dto.storageLocationId,
    storageLocationType: dto.storageLocationCode ?? "",
    createdAt: dto.createdAt ?? "",
    updatedAt: dto.updatedAt ?? "",
  };
}

/**
 * Get locations for a site, optionally filtered by storage location code (e.g. "NOT_ASSIGNED").
 * Used (T-6d-9) to resolve the site's NOT_ASSIGNED location directly, replacing both the
 * virtual-ID indirection and the site-blind `cachedNALocationId` module cache in
 * lib/api/inventory.ts - a real cross-site bug, since that cache was never keyed by site.
 */
export async function getSiteLocations(
  siteId: string,
  storageLocationCode?: string
): Promise<Location[]> {
  const result = await webApiClient.GET("/api/v1/sites/{siteId}/locations", {
    params: {
      path: { siteId },
      query: storageLocationCode ? { storageLocation: storageLocationCode } : undefined,
    },
  });
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toSiteLocation)
    .filter((loc): loc is Location => loc !== null);
}

// A record's identity (id + locationCode) is required to key/render it safely - see the
// toStorageLocationSummary comment above for why a missing id/code drops the row rather than
// defaulting it.
function toLocationWithCounts(
  dto: components["schemas"]["LocationWithCountsDTO"]
): LocationWithCounts | null {
  if (!dto.id || !dto.locationCode || !dto.locationType) {
    return null;
  }
  return {
    id: dto.id,
    locationType: dto.locationType as LocationType,
    locationCode: dto.locationCode,
    inventoryRecords: dto.inventoryRecords ?? 0,
    totalQuantity: dto.totalQuantity ?? 0,
    activeDisplayCount: dto.activeDisplayCount ?? 0,
    hasActiveDisplay: dto.hasActiveDisplay ?? false,
    createdAt: dto.createdAt ?? "",
    updatedAt: dto.updatedAt ?? "",
  };
}

/**
 * Site-scoped counterpart to `getLocationsWithCounts` (.specs/phase-6-inventory 6e, T-6e-9):
 * closes the legacy route's cross-site leak (no site predicate at all - every site's locations
 * and quantities returned and counted together). Used by both the storage-location tab bar and
 * the dashboard's location-utilization metric, which previously held two separate,
 * never-reconciled caches of the same site-blind endpoint.
 */
export async function getSiteLocationsWithCounts(
  siteId: string,
  locationType?: LocationType
): Promise<LocationWithCounts[]> {
  const result = await webApiClient.GET("/api/v1/sites/{siteId}/locations/with-counts", {
    params: {
      path: { siteId },
      query: locationType ? { type: locationType } : undefined,
    },
  });
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toLocationWithCounts)
    .filter((loc): loc is LocationWithCounts => loc !== null);
}

/**
 * Resolves a `LocationSelection`'s locationId to a real, site-scoped location UUID - passing
 * through any already-real id, and resolving the NOT_ASSIGNED virtual ID
 * (`LocationSelector`'s `__not_assigned__` placeholder) to this site's real NOT_ASSIGNED
 * location row via the v1 locations route. No caching: each call is already site-scoped and
 * cheap, and caching was the site-blind bug this replaces (`cachedNALocationId` in
 * lib/api/inventory.ts, never keyed by site).
 */
export async function resolveSiteLocationId(
  siteId: string,
  locationType: LocationType,
  locationId: string
): Promise<string> {
  if (locationType === LocationType.NOT_ASSIGNED && locationId === NOT_ASSIGNED_VIRTUAL_ID) {
    const locations = await getSiteLocations(siteId, "NOT_ASSIGNED");
    const naLocation = locations.find((loc) => loc.locationCode === "NA") ?? locations[0];
    if (!naLocation) {
      throw new Error("NOT_ASSIGNED location not found for this site");
    }
    return naLocation.id;
  }
  return locationId;
}

/** Create a location within an explicitly selected site's storage category. */
export async function createSiteLocation(
  siteId: string,
  body: components["schemas"]["CreateSiteLocationRequest"],
): Promise<Location> {
  const result = await webApiClient.POST("/api/v1/sites/{siteId}/locations", {
    params: { path: { siteId } }, body,
  });
  return requireSiteLocation(unwrapGeneratedResponse(result));
}

export async function updateSiteLocation(
  siteId: string,
  id: string,
  body: components["schemas"]["UpdateSiteLocationRequest"],
): Promise<Location> {
  const result = await webApiClient.PUT("/api/v1/sites/{siteId}/locations/{id}", {
    params: { path: { siteId, id } }, body,
  });
  return requireSiteLocation(unwrapGeneratedResponse(result));
}

export async function deleteSiteLocation(siteId: string, id: string): Promise<void> {
  const result = await webApiClient.DELETE("/api/v1/sites/{siteId}/locations/{id}", {
    params: { path: { siteId, id } },
  });
  unwrapGeneratedResponse(result);
}

function requireSiteLocation(dto: components["schemas"]["SiteLocationDTO"] | undefined): Location {
  const location = dto && toSiteLocation(dto);
  if (!location) throw new Error("Invalid location response");
  return location;
}

// Legacy reads remain for Phase 7 shipment/display/Kuji workflows only.
// ID-based legacy routes do not guarantee site ownership; do not add new callers.
export async function getLocations(storageLocationCode?: string): Promise<Location[]> {
  const params = storageLocationCode ? `?storageLocation=${storageLocationCode}` : "";
  return apiGet<Location[]>(`/api/locations${params}`);
}

/**
 * Get a location by ID.
 */
export async function getLocationById(id: string): Promise<Location> {
  return apiGet<Location>(`/api/locations/${id}`);
}

// Helper to get locations by type (uses storage location code mapping)

/**
 * Get all locations of a specific type.
 * Uses the STORAGE_LOCATION_CODES mapping to filter.
 */
export async function getLocationsByType(locationType: LocationType): Promise<Location[]> {
  if (locationType === LocationType.NOT_ASSIGNED) {
    return [];
  }
  const storageCode = STORAGE_LOCATION_CODES[locationType];
  return getLocations(storageCode);
}
