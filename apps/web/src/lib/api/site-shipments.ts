import { apiGet, apiPost, apiPut, apiPatch, apiDelete } from "./client";
import {
  Shipment,
  ShipmentRequest,
  ShipmentStatus,
  ReceiveShipmentRequest,
  PaginatedResponse,
} from "@/types/api";
import type {
  ShipmentDisplayStatus,
  ShipmentStatusCounts,
  OverrideShipmentStatusRequest,
  ShipmentFilters,
} from "./shipments";

// --- Site-scoped shipment routes (.specs/phase-7-shipments T-5, AC-5) --------------------------
// Every route here lives under /api/v1/sites/{siteId}/shipments/**, reusing the same
// ShipmentResponseDTO/ShipmentRequestDTO shapes as the legacy routes in ./shipments.ts - v1 added
// no new fields for this slice, only site scoping, so the existing @/types/api interfaces are
// reused as-is rather than introducing a parallel generated-client mapping layer.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/shipments`;
}

/** Get all shipments at a site, optionally filtered by status (no pagination). */
export async function getSiteShipments(
  siteId: string,
  status?: ShipmentStatus
): Promise<Shipment[]> {
  const path = status ? `${basePath(siteId)}?status=${status}` : basePath(siteId);
  return apiGet<Shipment[]>(path);
}

/** Get shipments at a site with server-side pagination and filtering. */
export async function getSiteShipmentsPaged(
  siteId: string,
  filters: ShipmentFilters = {},
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<Shipment>> {
  const params = new URLSearchParams();
  params.append("page", page.toString());
  params.append("size", size.toString());

  if (filters.displayStatus) {
    params.append("displayStatus", filters.displayStatus);
  } else if (filters.status) {
    params.append("status", filters.status);
  }
  if (filters.search) {
    params.append("search", filters.search);
  }
  if (filters.sortBy) {
    params.append("sortBy", filters.sortBy);
  }
  if (filters.sortDir) {
    params.append("sortDir", filters.sortDir);
  }

  return apiGet<PaginatedResponse<Shipment>>(`${basePath(siteId)}?${params.toString()}`);
}

/** Get counts for each display status (ACTIVE, PARTIAL, COMPLETED, OVERDUE) at a site. */
export async function getSiteDisplayStatusCounts(siteId: string): Promise<ShipmentStatusCounts> {
  return apiGet<ShipmentStatusCounts>(`${basePath(siteId)}/display-status-counts`);
}

/** Get a shipment by ID at a site. */
export async function getSiteShipmentById(siteId: string, id: string): Promise<Shipment> {
  return apiGet<Shipment>(`${basePath(siteId)}/${id}`);
}

/** Create a new shipment at a site. */
export async function createSiteShipment(
  siteId: string,
  data: ShipmentRequest
): Promise<Shipment> {
  return apiPost<Shipment, ShipmentRequest>(basePath(siteId), data);
}

/** Update an existing shipment at a site. */
export async function updateSiteShipment(
  siteId: string,
  id: string,
  data: ShipmentRequest
): Promise<Shipment> {
  return apiPut<Shipment, ShipmentRequest>(`${basePath(siteId)}/${id}`, data);
}

/** Delete a shipment at a site. */
export async function deleteSiteShipment(siteId: string, id: string): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/${id}`);
}

/** Receive a shipment at a site - updates inventory and creates stock movements. */
export async function receiveSiteShipment(
  siteId: string,
  id: string,
  data: ReceiveShipmentRequest
): Promise<Shipment> {
  return apiPost<Shipment, ReceiveShipmentRequest>(`${basePath(siteId)}/${id}/receive`, data);
}

/** Undo receiving a single shipment item at a site. */
export async function undoReceiveSiteShipmentItem(
  siteId: string,
  shipmentId: string,
  itemId: string
): Promise<Shipment> {
  return apiPost<Shipment, undefined>(
    `${basePath(siteId)}/${shipmentId}/items/${itemId}/undo-receive`,
    undefined
  );
}

/** Undo receipt for multiple shipment items at a site in one transaction. */
export async function undoReceiveSiteShipmentItems(
  siteId: string,
  shipmentId: string,
  itemIds: string[]
): Promise<Shipment> {
  return apiPost<Shipment, { itemIds: string[] }>(
    `${basePath(siteId)}/${shipmentId}/undo-receive`,
    { itemIds }
  );
}

/** Manually override a shipment's inventory status at a site. Reason is required. */
export async function overrideSiteShipmentStatus(
  siteId: string,
  id: string,
  data: OverrideShipmentStatusRequest
): Promise<Shipment> {
  return apiPatch<Shipment, OverrideShipmentStatusRequest>(`${basePath(siteId)}/${id}/status`, data);
}

/** Get all shipments at a site containing a specific product. */
export async function getSiteShipmentsByProduct(
  siteId: string,
  productId: string
): Promise<Shipment[]> {
  return apiGet<Shipment[]>(`${basePath(siteId)}/by-product/${productId}`);
}
