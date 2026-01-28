import { apiGet, apiPost } from "./client";
import { ShipmentStatus } from "@/types/api";

const BASE_PATH = "/api/tracking";

export interface TrackingLookupRequest {
  trackingNumber: string;
  carrier?: string;
}

export interface TrackingEvent {
  status: string;
  message: string;
  location: string;
  occurredAt: string;
}

export interface TrackingLookupResponse {
  trackingNumber: string;
  carrier: string;
  status: string;
  orderStatus: ShipmentStatus;
  dateOrdered?: string;
  expectedDelivery?: string;
  actualDelivery?: string;
  statusDetail: string;
  events: TrackingEvent[];
  lastUpdated: string;
}

/**
 * Lookup tracking information for a tracking number
 */
export async function lookupTracking(
  request: TrackingLookupRequest
): Promise<TrackingLookupResponse> {
  return apiPost<TrackingLookupResponse, TrackingLookupRequest>(
    `${BASE_PATH}/lookup`,
    request
  );
}

/**
 * Get tracking information for a tracking number
 */
export async function getTracking(
  trackingNumber: string
): Promise<TrackingLookupResponse> {
  return apiGet<TrackingLookupResponse>(`${BASE_PATH}/${trackingNumber}`);
}
