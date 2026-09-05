import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { webApiClient, unwrapGeneratedResponse } from "./generated-client";
import type { components } from "@mirai/api-client";
import {
  LocationType,
  Location,
  LocationRequest,
  LocationWithCounts,
  STORAGE_LOCATION_CODES,
} from "@/types/api";

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

// --- Site-scoped reads (Phase 3 Track D) -----------------------------------
// Used only by useStorageLocations (the storage-location category tab bar on the
// Locations page - the one workflow this feature migrated all the way to real UI).
// getLocations/getLocationsByType power unrelated workflows (shipment receiving, stock
// location selection, machine-display transfer) and stay on the legacy, unscoped
// endpoints - see .specs/track-d-web-client-adoption/log.md for why.

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

// --- Legacy, unscoped reads and all mutations -------------------------------
// Silently resolve to MAIN server-side (DEFAULT_SITE_CODE). Untouched by Track D.

/**
 * Fetch all locations with their inventory counts in a single request.
 * Replaces the N+1 pattern of fetching locations then counts individually.
 *
 * @param locationType Optional filter by location type
 * @returns List of locations with inventory record counts and total quantities
 */
export async function getLocationsWithCounts(
  locationType?: LocationType
): Promise<LocationWithCounts[]> {
  const params = locationType ? `?type=${locationType}` : "";
  return apiGet<LocationWithCounts[]>(`/api/locations/with-counts${params}`);
}

/**
 * Get a storage location by code.
 */
export async function getStorageLocationByCode(code: string): Promise<{
  id: string;
  code: string;
  name: string;
  hasDisplay: boolean;
  isDisplayOnly: boolean;
}> {
  return apiGet(`/api/storage-locations/by-code/${code}`);
}

// Location CRUD operations
// These use the unified /api/locations endpoints

/**
 * Get all locations (optionally filtered by storage location code).
 */
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

/**
 * Create a new location.
 */
export async function createLocation(data: LocationRequest): Promise<Location> {
  return apiPost<Location, LocationRequest>("/api/locations", data);
}

/**
 * Update a location.
 */
export async function updateLocation(
  id: string,
  data: Partial<LocationRequest>
): Promise<Location> {
  return apiPut<Location, Partial<LocationRequest>>(`/api/locations/${id}`, data);
}

/**
 * Delete a location.
 */
export async function deleteLocation(id: string): Promise<void> {
  return apiDelete<void>(`/api/locations/${id}`);
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
