"use client";

import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { getAuditLog } from "@/lib/api/stock-movements";
import { AuditLogFilters } from "@/types/api";

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
