"use client";

import { useState, useMemo } from "react";
import { SidebarTrigger } from "@/components/ui/sidebar";
import {
  AuditLogFilters,
  AuditLogFiltersState,
  AuditLogTable,
  AuditLogPagination,
  DEFAULT_AUDIT_LOG_FILTERS,
} from "@/components/audit-log";
import { useAuditLogs } from "@/hooks/queries/use-audit-log";
import { useUsers } from "@/hooks/queries/use-users";
import {
  AuditLogFilters as AuditLogFiltersType,
  StockMovementReason,
} from "@/types/api";

function buildApiFilters(state: AuditLogFiltersState): AuditLogFiltersType {
  const filters: AuditLogFiltersType = {};
  if (state.search) filters.search = state.search;
  if (state.actorId) filters.actorId = state.actorId;
  if (state.reason !== "all") filters.reason = state.reason as StockMovementReason;
  if (state.fromDate) filters.fromDate = state.fromDate;
  if (state.toDate) filters.toDate = state.toDate;
  return filters;
}

export default function AuditLogPage() {
  const [filters, setFilters] = useState<AuditLogFiltersState>(DEFAULT_AUDIT_LOG_FILTERS);
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const apiFilters = useMemo(() => buildApiFilters(filters), [filters]);
  const { data, isLoading } = useAuditLogs(apiFilters, page, pageSize);
  const { data: users } = useUsers();

  const handleFiltersChange = (next: AuditLogFiltersState) => {
    setFilters(next);
    setPage(0);
  };

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      {/* Page header */}
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Audit Log</h1>
      </div>

      {/* Search + advanced filters */}
      <AuditLogFilters
        state={filters}
        onChange={handleFiltersChange}
        users={users}
      />

      {/* Borderless table */}
      <div className="rounded-xl border bg-card overflow-hidden">
        <AuditLogTable
          data={data}
          isLoading={isLoading}
          page={page}
          onPageChange={setPage}
        />
      </div>

      <AuditLogPagination
        data={data}
        isLoading={isLoading}
        page={page}
        onPageChange={setPage}
      />
    </div>
  );
}
