import { apiGet } from "./client";
import {
  PaginatedResponse,
  AuditLogEntry,
  AuditLogFilters,
  AuditLog,
  AuditLogDetail,
} from "@/types/api";

// batchAdjustStock/transferStock/batchTransferStock/getStockMovementHistory (legacy, unscoped)
// were deleted in .specs/phase-6-inventory 6e, T-6e-7 - superseded by the site-scoped v1
// mutations in use-stock-mutations.ts and the site-scoped movement history in site-inventory.ts.
// No v1 audit-log route exists yet (T-6d-12 residual), so these three stay on legacy routes.

/**
 * Get audit log with optional filters (paginated)
 * @param filters - Optional filters for the audit log
 * @param page - Page number (0-indexed)
 * @param size - Page size
 */
export async function getAuditLog(
  filters: AuditLogFilters = {},
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<AuditLogEntry>> {
  const params = new URLSearchParams();
  params.append("page", page.toString());
  params.append("size", size.toString());

  if (filters.search) {
    params.append("search", filters.search);
  }
  if (filters.actorId) {
    params.append("actorId", filters.actorId);
  }
  if (filters.reason) {
    params.append("reason", filters.reason);
  }
  if (filters.fromDate) {
    params.append("fromDate", filters.fromDate);
  }
  if (filters.toDate) {
    params.append("toDate", filters.toDate);
  }

  return apiGet<PaginatedResponse<AuditLogEntry>>(
    `/api/stock-movements/audit-log?${params.toString()}`
  );
}

/**
 * Get grouped audit logs with optional filters (paginated)
 * Returns one row per user action, with summary info
 * @param filters - Optional filters for the audit log
 * @param page - Page number (0-indexed)
 * @param size - Page size
 */
export async function getAuditLogs(
  filters: AuditLogFilters = {},
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<AuditLog>> {
  const params = new URLSearchParams();
  params.append("page", page.toString());
  params.append("size", size.toString());

  if (filters.search) {
    params.append("search", filters.search);
  }
  if (filters.actorId) {
    params.append("actorId", filters.actorId);
  }
  if (filters.reason) {
    params.append("reason", filters.reason);
  }
  if (filters.reasons && filters.reasons.length > 0) {
    for (const r of filters.reasons) {
      params.append("reasons", r);
    }
  }
  if (filters.fromDate) {
    params.append("fromDate", filters.fromDate);
  }
  if (filters.toDate) {
    params.append("toDate", filters.toDate);
  }
  if (filters.productId) {
    params.append("productId", filters.productId);
  }
  if (filters.locationId) {
    params.append("locationId", filters.locationId);
  }

  return apiGet<PaginatedResponse<AuditLog>>(
    `/api/audit-logs?${params.toString()}`
  );
}

/**
 * Get audit log detail by ID (includes all movements)
 * @param id - The audit log ID
 */
export async function getAuditLogDetail(id: string): Promise<AuditLogDetail> {
  return apiGet<AuditLogDetail>(`/api/audit-logs/${id}`);
}
