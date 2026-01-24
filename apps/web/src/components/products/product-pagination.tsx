"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ProductPaginationProps {
  page: number;
  pageSize: number;
  totalItems: number;
  isLoading: boolean;
  onPageChange: (page: number) => void;
}

export function ProductPagination({
  page,
  pageSize,
  totalItems,
  isLoading,
  onPageChange,
}: ProductPaginationProps) {
  const totalPages = Math.ceil(totalItems / pageSize);
  const isFirst = page === 0;
  const isLast = page >= totalPages - 1;

  const startItem = page * pageSize + 1;
  const endItem = Math.min(startItem + pageSize - 1, totalItems);

  if (isLoading || totalItems === 0) {
    return null;
  }

  return (
    <div className="flex items-center justify-between px-2 pb-4">
      <p className="text-sm text-muted-foreground">
        Showing {startItem}-{endItem} of {totalItems}
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
