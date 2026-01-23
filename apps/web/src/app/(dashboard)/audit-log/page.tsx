"use client";

import { useState, useMemo } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  AuditLogFilters,
  AuditLogFiltersState,
  AuditLogTable,
  AuditLogPagination,
  DEFAULT_AUDIT_LOG_FILTERS,
} from "@/components/audit-log";
import { useAuditLog } from "@/hooks/queries/use-audit-log";
import { useUsers } from "@/hooks/queries/use-users";
import {
  AuditLogFilters as AuditLogFiltersType,
  StockMovementReason,
} from "@/types/api";

function buildApiFilters(state: AuditLogFiltersState): AuditLogFiltersType {
  const filters: AuditLogFiltersType = {};

  if (state.search) {
    filters.search = state.search;
  }
  if (state.actorId) {
    filters.actorId = state.actorId;
  }
  if (state.reason !== "all") {
    filters.reason = state.reason as StockMovementReason;
  }
  if (state.fromDate) {
    filters.fromDate = new Date(state.fromDate).toISOString();
  }
  if (state.toDate) {
    const endOfDay = new Date(state.toDate);
    endOfDay.setHours(23, 59, 59, 999);
    filters.toDate = endOfDay.toISOString();
  }

  return filters;
}

export default function AuditLogPage() {
  const [filters, setFilters] = useState<AuditLogFiltersState>(
    DEFAULT_AUDIT_LOG_FILTERS,
  );
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const apiFilters = useMemo(() => buildApiFilters(filters), [filters]);
  const { data, isLoading } = useAuditLog(apiFilters, page, pageSize);
  const { data: users } = useUsers();

  const handleFiltersChange = (next: AuditLogFiltersState) => {
    setFilters(next);
    setPage(0);
  };

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">Audit Log</h1>

      <AuditLogFilters
        state={filters}
        onChange={handleFiltersChange}
        users={users}
      />

      <Card className="py-0">
        <CardContent className="p-0">
          <AuditLogTable
            data={data}
            isLoading={isLoading}
            page={page}
            onPageChange={setPage}
          />
        </CardContent>
      </Card>

      <AuditLogPagination
        data={data}
        isLoading={isLoading}
        page={page}
        onPageChange={setPage}
      />
    </div>
  );
}
