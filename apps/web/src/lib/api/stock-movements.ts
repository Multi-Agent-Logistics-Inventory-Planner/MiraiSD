import { apiGet } from "./client";
import { PaginatedResponse, AuditLogEntry, AuditLogFilters } from "@/types/api";

// batchAdjustStock/transferStock/batchTransferStock/getStockMovementHistory (legacy, unscoped)
// were deleted in .specs/phase-6-inventory 6e, T-6e-7 - superseded by the site-scoped v1
// mutations in use-stock-mutations.ts and the site-scoped movement history in site-inventory.ts.
// The grouped audit-log list and detail moved to the site-scoped routes in site-audit-logs.ts;
// only the per-movement audit view below stays on the legacy MAIN-scoped route.

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
