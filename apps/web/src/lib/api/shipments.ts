import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  Shipment,
  ShipmentRequest,
  ShipmentStatus,
  ReceiveShipmentRequest,
} from "@/types/api";

const BASE_PATH = "/api/shipments";

/**
 * Get all shipments, optionally filtered by status
 */
export async function getShipments(status?: ShipmentStatus): Promise<Shipment[]> {
  const path = status ? `${BASE_PATH}?status=${status}` : BASE_PATH;
  return apiGet<Shipment[]>(path);
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
