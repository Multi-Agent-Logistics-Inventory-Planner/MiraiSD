import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  LocationType,
  LocationInventory,
  InventoryRequest,
  ProductInventoryResponse,
  InventoryTotal,
  DISPLAY_ONLY_LOCATION_TYPES,
} from "@/types/api";

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
 */
export async function createInventory(
  locationType: LocationType,
  locationId: string,
  data: InventoryRequest
): Promise<LocationInventory> {
  if (DISPLAY_ONLY_LOCATION_TYPES.includes(locationType)) {
    throw new Error(`${locationType} is display-only and does not support inventory`);
  }

  return createLocationInventory(locationId, data);
}

/**
 * Update inventory at a location.
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

  return updateLocationInventory(locationId, inventoryId, data);
}

/**
 * Delete inventory at a location.
 */
export async function deleteInventory(
  locationType: LocationType,
  locationId: string,
  inventoryId: string
): Promise<void> {
  if (DISPLAY_ONLY_LOCATION_TYPES.includes(locationType)) {
    throw new Error(`${locationType} is display-only and does not support inventory`);
  }

  return deleteLocationInventory(locationId, inventoryId);
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

