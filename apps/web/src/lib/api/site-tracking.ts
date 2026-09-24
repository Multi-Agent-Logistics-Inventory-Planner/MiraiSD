import { apiGet, apiPost } from "./client";
import type { TrackingLookupRequest, TrackingLookupResponse } from "./tracking";

// --- Site-scoped tracking routes (.specs/phase-7-shipments T-5, AC-5) --------------------------
// Mirrors ./site-shipments.ts: same TrackingLookupResponseDTO shape as the legacy routes, only
// site-scoped under /api/v1/sites/{siteId}/tracking/**.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/tracking`;
}

/** Lookup tracking information for a tracking number at a site. */
export async function lookupSiteTracking(
  siteId: string,
  request: TrackingLookupRequest
): Promise<TrackingLookupResponse> {
  return apiPost<TrackingLookupResponse, TrackingLookupRequest>(`${basePath(siteId)}/lookup`, request);
}

/** Get tracking information for a tracking number at a site. */
export async function getSiteTracking(
  siteId: string,
  trackingNumber: string
): Promise<TrackingLookupResponse> {
  return apiGet<TrackingLookupResponse>(`${basePath(siteId)}/${trackingNumber}`);
}
