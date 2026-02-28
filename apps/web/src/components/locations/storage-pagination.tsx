"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

interface StoragePaginationProps {
  page: number;
  pageSize: number;
  totalItems: number;
  isLoading: boolean;
  onPageChange: (page: number) => void;
}

export function StoragePagination({
  page,
  pageSize,
  totalItems,
  isLoading,
  onPageChange,
}: StoragePaginationProps) {
  const totalPages = Math.ceil(totalItems / pageSize);
  const isFirst = page === 1;
  const isLast = page >= totalPages;

  const startItem = (page - 1) * pageSize + 1;
  const endItem = Math.min(page * pageSize, totalItems);

  if (isLoading || totalItems === 0) {
    return null;
  }

  return (
    <div className="flex items-center justify-between px-2 pb-4 gap-2">
      <p className="text-xs sm:text-sm text-muted-foreground whitespace-nowrap">
        Showing {startItem}-{endItem} of {totalItems}
      </p>
      <div className="flex items-center gap-1 sm:gap-2">
        <Button
          variant="outline"
          size="icon"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst}
          className="h-8 w-8 sm:h-9 sm:w-auto sm:px-3"
        >
          <ChevronLeft className="h-4 w-4" />
          <span className="hidden sm:inline sm:ml-1">Previous</span>
        </Button>
        <span className="text-xs sm:text-sm text-muted-foreground whitespace-nowrap px-1 sm:px-2">
          Page {page} of {totalPages}
        </span>
        <Button
          variant="outline"
          size="icon"
          onClick={() => onPageChange(page + 1)}
          disabled={isLast}
          className="h-8 w-8 sm:h-9 sm:w-auto sm:px-3"
        >
          <span className="hidden sm:inline sm:mr-1">Next</span>
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
