import { apiGet, apiPost, apiDelete } from "./client";
import {
  LocationType,
  MachineDisplay,
  SetMachineDisplayRequest,
  SetMachineDisplayBatchRequest,
  PaginatedResponse,
} from "@/types/api";

export interface SwapMachineDisplayRequest {
  outgoingDisplayId: string;
  incomingProductId: string;
  locationType: LocationType;
  machineId: string;
  actorId?: string;
}

/**
 * Renew display request - ends current displays and creates new ones
 * with fresh startedAt timestamps for the same products.
 * Used when restocking the same product to reset tracking.
 */
export interface RenewDisplayRequest {
  locationType: LocationType;
  machineId: string;
  displayIds: string[];
  actorId?: string;
}

/**
 * Batch display swap request - supports both swap modes:
 * 1. Swap with products - remove displays and add new products
 * 2. Swap with another machine - trade displays between two machines
 */
export interface BatchDisplaySwapRequest {
  locationType: LocationType;
  machineId: string;
  /** Display IDs to remove from the current machine */
  displayIdsToRemove?: string[];
  /** Product IDs to add to the current machine */
  productIdsToAdd?: string[];
  /** For machine-to-machine swap: the target machine's location type */
  targetLocationType?: LocationType;
  /** For machine-to-machine swap: the target machine's ID */
  targetMachineId?: string;
  /** For machine-to-machine swap: display IDs to move FROM target machine TO current machine */
  displayIdsFromTarget?: string[];
  /** For machine-to-machine swap: display IDs to move FROM current machine TO target machine */
  displayIdsToTarget?: string[];
  actorId?: string;
}

/**
 * Get all active machine displays
 */
export async function getActiveDisplays(): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>("/api/machine-displays");
}

/**
 * Get active displays with pagination
 */
export async function getActiveDisplaysPaged(
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<MachineDisplay>> {
  return apiGet<PaginatedResponse<MachineDisplay>>(
    `/api/machine-displays/paged?page=${page}&size=${size}`
  );
}

/**
 * Get active displays for a specific location type
 */
export async function getActiveDisplaysByType(
  locationType: LocationType
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`/api/machine-displays/by-type/${locationType}`);
}

/**
 * Get current display for a specific machine
 */
export async function getCurrentDisplay(
  locationType: LocationType,
  machineId: string
): Promise<MachineDisplay | null> {
  try {
    return await apiGet<MachineDisplay>(
      `/api/machine-displays/${locationType}/${machineId}`
    );
  } catch {
    return null;
  }
}

/**
 * Get stale displays (active longer than threshold)
 */
export async function getStaleDisplays(
  thresholdDays?: number
): Promise<MachineDisplay[]> {
  const params = thresholdDays ? `?thresholdDays=${thresholdDays}` : "";
  return apiGet<MachineDisplay[]>(`/api/machine-displays/stale${params}`);
}

/**
 * Get stale displays for a specific location type
 */
export async function getStaleDisplaysByType(
  locationType: LocationType
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`/api/machine-displays/stale/${locationType}`);
}

/**
 * Get display history for a machine
 */
export async function getMachineHistory(
  locationType: LocationType,
  machineId: string
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(
    `/api/machine-displays/${locationType}/${machineId}/history`
  );
}

/**
 * Get display history for a machine with pagination
 */
export async function getMachineHistoryPaged(
  locationType: LocationType,
  machineId: string,
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<MachineDisplay>> {
  return apiGet<PaginatedResponse<MachineDisplay>>(
    `/api/machine-displays/${locationType}/${machineId}/history/paged?page=${page}&size=${size}`
  );
}

/**
 * Get display history for a product
 */
export async function getProductDisplayHistory(
  productId: string
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(
    `/api/machine-displays/product/${productId}/history`
  );
}

/**
 * Set or swap the display for a machine
 */
export async function setMachineDisplay(
  data: SetMachineDisplayRequest
): Promise<MachineDisplay> {
  return apiPost<MachineDisplay, SetMachineDisplayRequest>(
    "/api/machine-displays",
    data
  );
}

/**
 * Add multiple products to a machine's display in a single request.
 * Products already displayed are skipped (no error).
 */
export async function setMachineDisplayBatch(
  data: SetMachineDisplayBatchRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], SetMachineDisplayBatchRequest>(
    "/api/machine-displays/batch",
    data
  );
}

/**
 * Clear the display for a machine (ends all active displays)
 */
export async function clearMachineDisplay(
  locationType: LocationType,
  machineId: string,
  actorId?: string
): Promise<void> {
  const params = actorId ? `?actorId=${actorId}` : "";
  return apiDelete<void>(`/api/machine-displays/${locationType}/${machineId}${params}`);
}

/**
 * Clear a specific display by ID
 */
export async function clearDisplayById(
  displayId: string,
  actorId?: string
): Promise<void> {
  const params = actorId ? `?actorId=${actorId}` : "";
  return apiDelete<void>(`/api/machine-displays/by-id/${displayId}${params}`);
}

/**
 * Atomically swap one displayed product for another on the same machine.
 * Returns the updated list of active displays for the machine.
 */
export async function swapMachineDisplay(
  data: SwapMachineDisplayRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], SwapMachineDisplayRequest>(
    "/api/machine-displays/swap",
    data
  );
}

/**
 * Batch display swap operation that handles both swap modes in a single transaction:
 * 1. Swap with products - remove displays and add new products
 * 2. Swap with another machine - trade displays between two machines
 * Creates a single audit log entry with all changes.
 */
export async function batchSwapDisplay(
  data: BatchDisplaySwapRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], BatchDisplaySwapRequest>(
    "/api/machine-displays/batch-swap",
    data
  );
}

/**
 * Renew display records - ends current displays and creates new ones
 * with fresh startedAt timestamps for the same products.
 * Used when restocking the same product to reset tracking.
 */
export async function renewDisplays(
  data: RenewDisplayRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], RenewDisplayRequest>(
    "/api/machine-displays/renew",
    data
  );
}

/**
 * Get all active displays for a specific machine
 */
export async function getActiveDisplaysForMachine(
  locationType: LocationType,
  machineId: string
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(
    `/api/machine-displays/${locationType}/${machineId}/active`
  );
}

/**
 * Delete a display history record (Admin only)
 */
export async function deleteDisplayHistory(displayId: string): Promise<void> {
  return apiDelete<void>(`/api/machine-displays/history/${displayId}`);
}
