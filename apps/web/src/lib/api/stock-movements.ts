import { apiGet, apiPost } from "./client";
import {
  LocationType,
  StockMovement,
  AdjustStockRequest,
  TransferStockRequest,
  BatchTransferStockRequest,
  PaginatedResponse,
  AuditLogEntry,
  AuditLogFilters,
  AuditLog,
  AuditLogDetail,
} from "@/types/api";

/**
 * Adjust stock quantity for an inventory item
 * @param locationType - The type of storage location
 * @param inventoryId - The inventory record ID
 * @param data - Adjustment details (quantity change, reason, actor, notes)
 */
export async function adjustStock(
  locationType: LocationType,
  inventoryId: string,
  data: AdjustStockRequest
): Promise<StockMovement> {
  return apiPost<StockMovement, AdjustStockRequest>(
    `/api/stock-movements/${locationType}/${inventoryId}/adjust`,
    data
  );
}

/**
 * Transfer stock between locations (single item)
 */
export async function transferStock(
  data: TransferStockRequest
): Promise<StockMovement> {
  return apiPost<StockMovement, TransferStockRequest>(
    "/api/stock-movements/transfer",
    data
  );
}

/**
 * Transfer multiple inventory items in a single batch.
 * Creates one audit log entry for the entire operation.
 */
export async function batchTransferStock(
  data: BatchTransferStockRequest
): Promise<void> {
  return apiPost<void, BatchTransferStockRequest>(
    "/api/stock-movements/batch-transfer",
    data
  );
}

/**
 * Get stock movement history for a product (paginated)
 * @param productId - The product ID to get history for
 * @param page - Page number (0-indexed)
 * @param size - Page size
 */
export async function getStockMovementHistory(
  productId: string,
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<StockMovement>> {
  return apiGet<PaginatedResponse<StockMovement>>(
    `/api/stock-movements/history/${productId}?page=${page}&size=${size}`
  );
}

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
