import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  Shipment,
  ShipmentRequest,
  ShipmentStatus,
  ReceiveShipmentRequest,
  PaginatedResponse,
} from "@/types/api";

const BASE_PATH = "/api/shipments";

export type ShipmentDisplayStatus = "ACTIVE" | "PARTIAL" | "COMPLETED";

export interface ShipmentFilters {
  status?: ShipmentStatus;
  displayStatus?: ShipmentDisplayStatus;
  search?: string;
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

  return apiGet<PaginatedResponse<Shipment>>(`${BASE_PATH}?${params.toString()}`);
}

/**
 * Get counts for each display status (ACTIVE, PARTIAL, COMPLETED)
 */
export async function getDisplayStatusCounts(): Promise<Record<ShipmentDisplayStatus, number>> {
  return apiGet<Record<ShipmentDisplayStatus, number>>(`${BASE_PATH}/display-status-counts`);
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
 * Undo receiving a shipment - reverses inventory changes and resets status to PENDING
 */
export async function undoReceiveShipment(id: string): Promise<Shipment> {
  return apiPost<Shipment, undefined>(`${BASE_PATH}/${id}/undo-receive`, undefined);
}

/**
 * Get all shipments containing a specific product
 */
export async function getShipmentsByProduct(productId: string): Promise<Shipment[]> {
  return apiGet<Shipment[]>(`${BASE_PATH}/by-product/${productId}`);
}
