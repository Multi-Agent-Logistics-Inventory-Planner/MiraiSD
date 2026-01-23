"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PaginatedResponse } from "@/types/api";

interface AuditLogPaginationProps {
  data?: PaginatedResponse<unknown>;
  isLoading: boolean;
  page: number;
  onPageChange: (page: number) => void;
}

export function AuditLogPagination({
  data,
  isLoading,
  page,
  onPageChange,
}: AuditLogPaginationProps) {
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const isFirst = data?.first ?? true;
  const isLast = data?.last ?? true;

  const startItem = page * (data?.size ?? 20) + 1;
  const endItem = Math.min(
    startItem + (data?.content?.length ?? 0) - 1,
    totalElements,
  );

  if (isLoading || totalElements === 0) {
    return null;
  }

  return (
    <div className="flex items-center justify-between px-2 pb-4">
      <p className="text-sm text-muted-foreground">
        Showing {startItem}-{endItem} of {totalElements}
      </p>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst}
        >
          <ChevronLeft className="h-4 w-4" />
          Previous
        </Button>
        <span className="text-sm text-muted-foreground">
          Page {page + 1} of {totalPages}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={isLast}
        >
          Next
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
