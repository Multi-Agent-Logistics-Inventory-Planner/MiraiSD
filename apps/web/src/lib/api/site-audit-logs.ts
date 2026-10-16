import { apiGet } from "./client";
import { PaginatedResponse, AuditLog, AuditLogDetail, AuditLogFilters } from "@/types/api";

// Site-scoped grouped audit-log routes (.specs/phase-7-notifications-reviews-audit). The response
// shapes match the legacy /api/audit-logs routes.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/audit-logs`;
}

/** Get grouped audit logs at a site (one row per user action), paginated. */
export async function getSiteAuditLogs(
  siteId: string,
  filters: AuditLogFilters = {},
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<AuditLog>> {
  const params = new URLSearchParams();
  params.append("page", page.toString());
  params.append("size", size.toString());

  if (filters.search) params.append("search", filters.search);
  if (filters.actorId) params.append("actorId", filters.actorId);
  if (filters.reason) params.append("reason", filters.reason);
  if (filters.reasons && filters.reasons.length > 0) {
    for (const r of filters.reasons) {
      params.append("reasons", r);
    }
  }
  if (filters.fromDate) params.append("fromDate", filters.fromDate);
  if (filters.toDate) params.append("toDate", filters.toDate);
  if (filters.productId) params.append("productId", filters.productId);
  if (filters.locationId) params.append("locationId", filters.locationId);

  return apiGet<PaginatedResponse<AuditLog>>(`${basePath(siteId)}?${params.toString()}`);
}

/** Get one audit log with all its movements, within a site. */
export async function getSiteAuditLogDetail(siteId: string, id: string): Promise<AuditLogDetail> {
  return apiGet<AuditLogDetail>(`${basePath(siteId)}/${id}`);
}
