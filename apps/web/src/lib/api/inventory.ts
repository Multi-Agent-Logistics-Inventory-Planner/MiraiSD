import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  LocationType,
  LocationInventory,
  InventoryRequest,
  ProductInventoryResponse,
  InventoryTotal,
  DISPLAY_ONLY_LOCATION_TYPES,
  type Location,
} from "@/types/api";
import { getLocations } from "./locations";

/** Virtual ID used for NOT_ASSIGNED when no real ID is available */
const NOT_ASSIGNED_VIRTUAL_ID = "__not_assigned__";

/** Cached NA location ID to avoid repeated lookups */
let cachedNALocationId: string | null = null;

/**
 * Get the actual NA location ID for NOT_ASSIGNED inventory operations.
 * Caches the result to avoid repeated API calls.
 */
async function getNALocationId(): Promise<string> {
  if (cachedNALocationId) {
    return cachedNALocationId;
  }

  // Get locations within NOT_ASSIGNED storage location
  const locations = await getLocations("NOT_ASSIGNED");

  const naLocation = locations.find((loc) => loc.locationCode === "NA");
  if (!naLocation) {
    throw new Error("NA location not found within NOT_ASSIGNED storage location");
  }

  cachedNALocationId = naLocation.id;
  return cachedNALocationId;
}

/**
 * Resolve location ID, handling the NOT_ASSIGNED virtual ID case.
 */
async function resolveLocationId(
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

// Unified inventory API using /api/locations/{locationId}/inventory endpoints

/**
 * Get all inventory at a specific location.
 */
export async function getLocationInventory(
  locationId: string
): Promise<LocationInventory[]> {
  return apiGet<LocationInventory[]>(`/api/locations/${locationId}/inventory`);
}

/**
 * Get a specific inventory record.
 */
export async function getLocationInventoryItem(
  locationId: string,
  inventoryId: string
): Promise<LocationInventory> {
  return apiGet<LocationInventory>(
    `/api/locations/${locationId}/inventory/${inventoryId}`
  );
}

/**
 * Create inventory at a location.
 */
export async function createLocationInventory(
  locationId: string,
  data: InventoryRequest
): Promise<LocationInventory> {
  return apiPost<LocationInventory, InventoryRequest>(
    `/api/locations/${locationId}/inventory`,
    data
  );
}

/**
 * Update an inventory record.
 */
export async function updateLocationInventory(
  locationId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<LocationInventory> {
  return apiPut<LocationInventory, InventoryRequest>(
    `/api/locations/${locationId}/inventory/${inventoryId}`,
    data
  );
}

/**
 * Delete an inventory record.
 */
export async function deleteLocationInventory(
  locationId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `/api/locations/${locationId}/inventory/${inventoryId}`
  );
}

/**
 * Get all inventory for a storage location category (e.g., all NOT_ASSIGNED inventory).
 */
export async function getStorageLocationInventory(
  storageLocationId: string
): Promise<LocationInventory[]> {
  return apiGet<LocationInventory[]>(
    `/api/storage-locations/${storageLocationId}/inventory`
  );
}

// Generic helpers that maintain backward compatibility

/**
 * Get inventory by location type and location ID.
 * For NOT_ASSIGNED, locationId should be the storage location ID.
 */
export async function getInventoryByLocation(
  locationType: LocationType,
  locationId: string
): Promise<LocationInventory[]> {
  if (DISPLAY_ONLY_LOCATION_TYPES.includes(locationType)) {
    throw new Error(`${locationType} is display-only and does not support inventory`);
  }

  if (locationType === LocationType.NOT_ASSIGNED) {
    // For NOT_ASSIGNED, use the storage location inventory endpoint
    return getStorageLocationInventory(locationId);
  }

  return getLocationInventory(locationId);
}

/**
 * Create inventory at a location.
 * Handles NOT_ASSIGNED by automatically resolving the virtual ID to the actual NA location.
 */
export async function createInventory(
  locationType: LocationType,
  locationId: string,
  data: InventoryRequest
): Promise<LocationInventory> {
  if (DISPLAY_ONLY_LOCATION_TYPES.includes(locationType)) {
    throw new Error(`${locationType} is display-only and does not support inventory`);
  }

  const resolvedId = await resolveLocationId(locationType, locationId);
  return createLocationInventory(resolvedId, data);
}

/**
 * Update inventory at a location.
 * Handles NOT_ASSIGNED by automatically resolving the virtual ID to the actual NA location.
 */
export async function updateInventory(
  locationType: LocationType,
  locationId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<LocationInventory> {
  if (DISPLAY_ONLY_LOCATION_TYPES.includes(locationType)) {
    throw new Error(`${locationType} is display-only and does not support inventory`);
  }

  const resolvedId = await resolveLocationId(locationType, locationId);
  return updateLocationInventory(resolvedId, inventoryId, data);
}

/**
 * Delete inventory at a location.
 * Handles NOT_ASSIGNED by automatically resolving the virtual ID to the actual NA location.
 */
export async function deleteInventory(
  locationType: LocationType,
  locationId: string,
  inventoryId: string
): Promise<void> {
  if (DISPLAY_ONLY_LOCATION_TYPES.includes(locationType)) {
    throw new Error(`${locationType} is display-only and does not support inventory`);
  }

  const resolvedId = await resolveLocationId(locationType, locationId);
  return deleteLocationInventory(resolvedId, inventoryId);
}

// Aggregated inventory queries (already using correct endpoints)

/**
 * Fetch all inventory entries for a product across all location types in a single request.
 */
export async function getProductInventoryEntries(
  productId: string
): Promise<ProductInventoryResponse> {
  return apiGet<ProductInventoryResponse>(`/api/inventory/by-product/${productId}`);
}

/**
 * Fetch aggregated inventory totals for all products in a single query.
 */
export async function getInventoryTotals(): Promise<InventoryTotal[]> {
  return apiGet<InventoryTotal[]>("/api/inventory/totals");
}

