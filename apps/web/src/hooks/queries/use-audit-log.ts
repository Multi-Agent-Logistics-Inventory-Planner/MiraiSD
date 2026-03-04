"use client";

import { useQuery, keepPreviousData } from "@tanstack/react-query";
import {
  getAuditLog,
  getAuditLogs,
  getAuditLogDetail,
} from "@/lib/api/stock-movements";
import { AuditLogFilters } from "@/types/api";

/**
 * @deprecated Use useAuditLogs instead for grouped audit logs
 */
export function useAuditLog(
  filters: AuditLogFilters = {},
  page: number = 0,
  size: number = 20
) {
  return useQuery({
    queryKey: ["audit-log", filters, page, size],
    queryFn: () => getAuditLog(filters, page, size),
    placeholderData: keepPreviousData,
  });
}

/**
 * Fetch grouped audit logs (one row per user action)
 */
export function useAuditLogs(
  filters: AuditLogFilters = {},
  page: number = 0,
  size: number = 20
) {
  return useQuery({
    queryKey: ["audit-logs", filters, page, size],
    queryFn: () => getAuditLogs(filters, page, size),
    placeholderData: keepPreviousData,
  });
}

/**
 * Fetch audit log detail by ID (includes all movements)
 */
export function useAuditLogDetail(id: string | null) {
  return useQuery({
    queryKey: ["audit-log-detail", id],
    queryFn: () => (id ? getAuditLogDetail(id) : Promise.reject("No ID")),
    enabled: !!id,
  });
}
