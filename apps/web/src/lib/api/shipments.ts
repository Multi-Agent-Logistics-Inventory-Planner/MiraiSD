import { apiGet, apiPost, apiPut, apiPatch, apiDelete } from "./client";
import {
  Shipment,
  ShipmentRequest,
  ShipmentStatus,
  ReceiveShipmentRequest,
  PaginatedResponse,
} from "@/types/api";

const BASE_PATH = "/api/shipments";

export type ShipmentDisplayStatus =
  | "ACTIVE"
  | "AWAITING_RECEIPT"
  | "PARTIAL"
  | "COMPLETED"
  | "FAILED";

// Type for the counts response (includes OVERDUE which overlaps with ACTIVE/PARTIAL)
export type ShipmentStatusCounts = Record<ShipmentDisplayStatus | "OVERDUE", number>;

export interface OverrideShipmentStatusRequest {
  status: ShipmentStatus;
  reason: string;
}

export type SortDirection = "asc" | "desc";

export interface ShipmentFilters {
  status?: ShipmentStatus;
  displayStatus?: ShipmentDisplayStatus;
  search?: string;
  sortBy?: string;
  sortDir?: SortDirection;
}

/**
 * Get all shipments, optionally filtered by status (legacy, no pagination)
 */
export async function getShipments(status?: ShipmentStatus): Promise<Shipment[]> {
  const path = status ? `${BASE_PATH}?status=${status}` : BASE_PATH;
  return apiGet<Shipment[]>(path);
}

/**
 * Get shipments with server-side pagination and filtering
 */
export async function getShipmentsPaged(
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

  return apiGet<PaginatedResponse<Shipment>>(`${BASE_PATH}?${params.toString()}`);
}

/**
 * Get counts for each display status (ACTIVE, PARTIAL, COMPLETED, OVERDUE)
 */
export async function getDisplayStatusCounts(): Promise<ShipmentStatusCounts> {
  return apiGet<ShipmentStatusCounts>(`${BASE_PATH}/display-status-counts`);
}

/**
 * Get a shipment by ID
 */
export async function getShipmentById(id: string): Promise<Shipment> {
  return apiGet<Shipment>(`${BASE_PATH}/${id}`);
}

/**
 * Create a new shipment
 */
export async function createShipment(data: ShipmentRequest): Promise<Shipment> {
  return apiPost<Shipment, ShipmentRequest>(BASE_PATH, data);
}

/**
 * Update an existing shipment
 */
export async function updateShipment(
  id: string,
  data: ShipmentRequest
): Promise<Shipment> {
  return apiPut<Shipment, ShipmentRequest>(`${BASE_PATH}/${id}`, data);
}

/**
 * Delete a shipment
 */
export async function deleteShipment(id: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${id}`);
}

/**
 * Receive a shipment - updates inventory and creates stock movements
 */
export async function receiveShipment(
  id: string,
  data: ReceiveShipmentRequest
): Promise<Shipment> {
  return apiPost<Shipment, ReceiveShipmentRequest>(
    `${BASE_PATH}/${id}/receive`,
    data
  );
}

/**
 * Undo receiving a single shipment item - reverses inventory changes for just that item
 */
export async function undoReceiveShipmentItem(
  shipmentId: string,
  itemId: string
): Promise<Shipment> {
  return apiPost<Shipment, undefined>(
    `${BASE_PATH}/${shipmentId}/items/${itemId}/undo-receive`,
    undefined
  );
}

/**
 * Manually override a shipment's inventory status. Reason is required.
 */
export async function overrideShipmentStatus(
  id: string,
  data: OverrideShipmentStatusRequest
): Promise<Shipment> {
  return apiPatch<Shipment, OverrideShipmentStatusRequest>(
    `${BASE_PATH}/${id}/status`,
    data
  );
}

/**
 * Get all shipments containing a specific product
 */
export async function getShipmentsByProduct(productId: string): Promise<Shipment[]> {
  return apiGet<Shipment[]>(`${BASE_PATH}/by-product/${productId}`);
}
