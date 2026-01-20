"use client";

import { Badge } from "@/components/ui/badge";
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
import { cn } from "@/lib/utils";
import type { StockMovement } from "@/types/api";

interface MovementHistoryTableProps {
  movements: StockMovement[];
  isLoading?: boolean;
  error?: Error | null;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  productById?: Record<string, { name: string; sku: string }>;
}

function formatLocationType(value: string) {
  return value
    .split("_")
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(" ");
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function formatId(value?: string | null) {
  if (!value) return "—";
  if (value.length <= 10) return value;
  return `${value.slice(0, 6)}...${value.slice(-4)}`;
}

function formatLocation(movement: StockMovement) {
  const from = movement.fromLocationId
    ? formatId(movement.fromLocationId)
    : null;
  const to = movement.toLocationId ? formatId(movement.toLocationId) : null;
  if (from || to) {
    return `${from ?? "—"} -> ${to ?? "—"}`;
  }
  return formatLocationType(movement.locationType);
}

export function MovementHistoryTable({
  movements,
  isLoading,
  error,
  page,
  totalPages,
  onPageChange,
  productById,
}: MovementHistoryTableProps) {
  const pageCount = Math.max(1, totalPages);
  const canPrev = page > 0;
  const canNext = page + 1 < pageCount;

  return (
    <div className="space-y-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Timestamp</TableHead>
            <TableHead>Product</TableHead>
            <TableHead className="text-right">Change</TableHead>
            <TableHead>Reason</TableHead>
            <TableHead>From {"->"} To</TableHead>
            <TableHead>Actor</TableHead>
            <TableHead>Notes</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading ? (
            Array.from({ length: 6 }).map((_, i) => (
              <TableRow key={i}>
                <TableCell>
                  <Skeleton className="h-4 w-28" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-40" />
                </TableCell>
                <TableCell className="text-right">
                  <Skeleton className="ml-auto h-4 w-16" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-5 w-20" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-28" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-20" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-32" />
                </TableCell>
              </TableRow>
            ))
          ) : error ? (
            <TableRow>
              <TableCell
                colSpan={7}
                className="py-6 text-center text-sm text-destructive"
              >
                {error.message}
              </TableCell>
            </TableRow>
          ) : movements.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={7}
                className="py-6 text-center text-sm text-muted-foreground"
              >
                No stock movements found.
              </TableCell>
            </TableRow>
          ) : (
            movements.map((movement) => {
              const item =
                movement.item ??
                (movement.itemId ? productById?.[movement.itemId] : undefined);
              const itemName = item?.name ?? "Unknown product";
              const itemSku = item?.sku ?? formatId(movement.itemId);
              const change = movement.quantityChange ?? 0;
              const changeLabel = `${change > 0 ? "+" : ""}${change}`;
              const changeClass =
                change > 0
                  ? "text-emerald-700"
                  : change < 0
                    ? "text-red-600"
                    : "text-muted-foreground";

              return (
                <TableRow key={movement.id}>
                  <TableCell className="text-sm text-muted-foreground">
                    {formatDate(movement.at)}
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-col">
                      <span className="font-medium">{itemName}</span>
                      <span className="text-xs text-muted-foreground">
                        {itemSku}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell
                    className={cn("text-right font-semibold", changeClass)}
                  >
                    {changeLabel}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className="text-xs">
                      {movement.reason}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    {formatLocation(movement)}
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    {formatId(movement.actorId)}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {movement.metadata?.notes ?? "—"}
                  </TableCell>
                </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>

      <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
        <span>
          Page {page + 1} of {pageCount}
        </span>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!canPrev}
            onClick={() => onPageChange(Math.max(0, page - 1))}
          >
            Previous
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!canNext}
            onClick={() => onPageChange(page + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
