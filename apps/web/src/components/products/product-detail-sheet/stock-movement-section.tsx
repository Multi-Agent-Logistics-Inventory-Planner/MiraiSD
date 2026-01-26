"use client";

import { useState } from "react";
import { History, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useMovementHistory } from "@/hooks/queries/use-movement-history";
import type { StockMovementReason, LocationType } from "@/types/api";

interface StockMovementSectionProps {
  productId: string;
}

const PAGE_SIZE = 20;

const REASON_LABELS: Record<StockMovementReason, string> = {
  INITIAL_STOCK: "Initial Stock",
  RESTOCK: "Restock",
  SALE: "Sale",
  DAMAGE: "Damage",
  ADJUSTMENT: "Adjustment",
  RETURN: "Return",
  TRANSFER: "Transfer",
};

const LOCATION_TYPE_SHORT: Record<LocationType, string> = {
  BOX_BIN: "Box",
  SINGLE_CLAW_MACHINE: "S.Claw",
  DOUBLE_CLAW_MACHINE: "D.Claw",
  KEYCHAIN_MACHINE: "Keychain",
  CABINET: "Cabinet",
  RACK: "Rack",
};

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-12" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

export function StockMovementSection({ productId }: StockMovementSectionProps) {
  const [page, setPage] = useState(0);
  const { data, isLoading, error } = useMovementHistory(
    productId,
    page,
    PAGE_SIZE
  );

  const movements = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  if (error) {
    return (
      <div className="rounded-lg border bg-card p-4 text-center text-sm text-muted-foreground">
        Failed to load movement history
      </div>
    );
  }

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatChange = (change: number) => {
    if (change > 0) {
      return <span className="text-green-600 dark:text-green-400">+{change}</span>;
    }
    if (change < 0) {
      return <span className="text-red-600 dark:text-red-400">{change}</span>;
    }
    return <span className="text-muted-foreground">0</span>;
  };

  const getLocationDisplay = (
    locationType?: LocationType,
    fromLocationId?: string,
    toLocationId?: string
  ) => {
    if (!locationType) return "-";
    const typeLabel = LOCATION_TYPE_SHORT[locationType];
    if (fromLocationId && toLocationId) {
      return `${typeLabel} (transfer)`;
    }
    return typeLabel;
  };

  return (
    <div className="space-y-3">
      <div className="rounded-lg border bg-card overflow-hidden">
        <Table>
          <TableHeader className="bg-muted">
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead className="text-right">Change</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Location</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableSkeleton />
            ) : movements.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={4}
                  className="h-24 text-center text-muted-foreground"
                >
                  <div className="flex flex-col items-center gap-2">
                    <History className="h-8 w-8 text-muted-foreground/50" />
                    <span>No movement history</span>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              movements.map((movement) => (
                <TableRow key={movement.id}>
                  <TableCell className="text-sm">
                    {formatDate(movement.at)}
                  </TableCell>
                  <TableCell className="text-right font-mono text-sm">
                    {formatChange(movement.quantityChange)}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {REASON_LABELS[movement.reason]}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {getLocationDisplay(
                      movement.locationType,
                      movement.fromLocationId,
                      movement.toLocationId
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            Showing {page * PAGE_SIZE + 1}-
            {Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements}
          </div>
          <div className="flex gap-1">
            <Button
              variant="outline"
              size="icon"
              className="h-8 w-8"
              disabled={page === 0 || isLoading}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              className="h-8 w-8"
              disabled={page >= totalPages - 1 || isLoading}
              onClick={() => setPage((p) => p + 1)}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
