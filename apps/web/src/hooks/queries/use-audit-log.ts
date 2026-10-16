"use client";

import { useQuery, keepPreviousData, skipToken } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getAuditLog } from "@/lib/api/stock-movements";
import { getSiteAuditLogDetail, getSiteAuditLogs } from "@/lib/api/site-audit-logs";
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
 * Fetch grouped audit logs (one row per user action) for the current site
 */
export function useAuditLogs(
  filters: AuditLogFilters = {},
  page: number = 0,
  size: number = 20
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["audit-logs", siteId, filters, page, size],
    queryFn: siteId ? () => getSiteAuditLogs(siteId, filters, page, size) : skipToken,
    placeholderData: keepPreviousData,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}

/**
 * Fetch audit log detail by ID (includes all movements) within the current site
 */
export function useAuditLogDetail(id: string | null) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["audit-log-detail", siteId, id],
    queryFn: siteId && id ? () => getSiteAuditLogDetail(siteId, id) : skipToken,
  });

  return {
    ...query,
    isLoading: isSiteLoading || query.isLoading,
    error: siteError ?? query.error,
  };
}
