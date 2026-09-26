import { apiGet, apiPost, apiDelete } from "./client";
import {
  LocationType,
  MachineDisplay,
  SetMachineDisplayRequest,
  SetMachineDisplayBatchRequest,
  PaginatedResponse,
} from "@/types/api";
import type {
  SwapMachineDisplayRequest,
  BatchClearDisplaysRequest,
  RenewDisplayRequest,
  BatchDisplaySwapRequest,
} from "./machine-displays";

// Every route here lives under /api/v1/sites/{siteId}/machine-displays/**, reusing the same
// MachineDisplay*/request DTO shapes as the legacy routes in ./machine-displays.ts - v1 added no
// new fields for this slice, only site scoping, so the existing @/types/api interfaces are reused
// as-is rather than introducing a parallel generated-client mapping layer.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/machine-displays`;
}

/** Get all active machine displays at a site. */
export async function getSiteActiveDisplays(siteId: string): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(basePath(siteId));
}

/** Get active displays at a site with pagination. */
export async function getSiteActiveDisplaysPaged(
  siteId: string,
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<MachineDisplay>> {
  return apiGet<PaginatedResponse<MachineDisplay>>(
    `${basePath(siteId)}/paged?page=${page}&size=${size}`
  );
}

/** Get active displays at a site for a specific location type. */
export async function getSiteActiveDisplaysByType(
  siteId: string,
  locationType: LocationType
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`${basePath(siteId)}/by-type/${locationType}`);
}

/** Get current display for a specific machine at a site. */
export async function getSiteCurrentDisplay(
  siteId: string,
  locationType: LocationType,
  machineId: string
): Promise<MachineDisplay | null> {
  try {
    return await apiGet<MachineDisplay>(`${basePath(siteId)}/${locationType}/${machineId}`);
  } catch {
    return null;
  }
}

/** Get stale displays (active longer than threshold) at a site. */
export async function getSiteStaleDisplays(
  siteId: string,
  thresholdDays?: number
): Promise<MachineDisplay[]> {
  const params = thresholdDays ? `?thresholdDays=${thresholdDays}` : "";
  return apiGet<MachineDisplay[]>(`${basePath(siteId)}/stale${params}`);
}

/** Get stale displays at a site for a specific location type. */
export async function getSiteStaleDisplaysByType(
  siteId: string,
  locationType: LocationType
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`${basePath(siteId)}/stale/${locationType}`);
}

/** Get display history for a machine at a site. */
export async function getSiteMachineHistory(
  siteId: string,
  locationType: LocationType,
  machineId: string
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`${basePath(siteId)}/${locationType}/${machineId}/history`);
}

/** Get display history for a machine at a site with pagination. */
export async function getSiteMachineHistoryPaged(
  siteId: string,
  locationType: LocationType,
  machineId: string,
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<MachineDisplay>> {
  return apiGet<PaginatedResponse<MachineDisplay>>(
    `${basePath(siteId)}/${locationType}/${machineId}/history/paged?page=${page}&size=${size}`
  );
}

/** Get display history for a product at a site. */
export async function getSiteProductDisplayHistory(
  siteId: string,
  productId: string
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`${basePath(siteId)}/product/${productId}/history`);
}

/** Set or swap the display for a machine at a site. */
export async function setSiteMachineDisplay(
  siteId: string,
  data: SetMachineDisplayRequest
): Promise<MachineDisplay> {
  return apiPost<MachineDisplay, SetMachineDisplayRequest>(basePath(siteId), data);
}

/** Add multiple products to a machine's display at a site in a single request. */
export async function setSiteMachineDisplayBatch(
  siteId: string,
  data: SetMachineDisplayBatchRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], SetMachineDisplayBatchRequest>(
    `${basePath(siteId)}/batch`,
    data
  );
}

/** Clear the display for a machine at a site (ends all active displays). The actor is always the authenticated principal, resolved server-side. */
export async function clearSiteMachineDisplay(
  siteId: string,
  locationType: LocationType,
  machineId: string
): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/${locationType}/${machineId}`);
}

/** Clear a specific display by ID at a site. The actor is always the authenticated principal, resolved server-side. */
export async function clearSiteDisplayById(siteId: string, displayId: string): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/by-id/${displayId}`);
}

/** Atomically swap one displayed product for another on the same machine at a site. */
export async function swapSiteMachineDisplay(
  siteId: string,
  data: SwapMachineDisplayRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], SwapMachineDisplayRequest>(`${basePath(siteId)}/swap`, data);
}

/** Batch display swap operation (both swap modes) at a site in a single transaction. */
export async function batchSwapSiteDisplay(
  siteId: string,
  data: BatchDisplaySwapRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], BatchDisplaySwapRequest>(
    `${basePath(siteId)}/batch-swap`,
    data
  );
}

/** Batch-clear multiple displays on the same machine at a site in one transaction. */
export async function batchClearSiteDisplays(
  siteId: string,
  data: BatchClearDisplaysRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], BatchClearDisplaysRequest>(
    `${basePath(siteId)}/batch-clear`,
    data
  );
}

/** Renew display records at a site with fresh startedAt timestamps. */
export async function renewSiteDisplays(
  siteId: string,
  data: RenewDisplayRequest
): Promise<MachineDisplay[]> {
  return apiPost<MachineDisplay[], RenewDisplayRequest>(`${basePath(siteId)}/renew`, data);
}

/** Get all active displays for a specific machine at a site. */
export async function getSiteActiveDisplaysForMachine(
  siteId: string,
  locationType: LocationType,
  machineId: string
): Promise<MachineDisplay[]> {
  return apiGet<MachineDisplay[]>(`${basePath(siteId)}/${locationType}/${machineId}/active`);
}

/** Delete a display history record at a site (Admin only). */
export async function deleteSiteDisplayHistory(siteId: string, displayId: string): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/history/${displayId}`);
}
